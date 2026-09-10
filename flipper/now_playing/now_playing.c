#include "core/np_model.h"
#include "core/np_protocol.h"
#include "np_ble_profile.h"
#include "ui/np_view.h"
#include <bt/bt_service/bt.h>
#include <furi_hal.h>
#include <gui/gui.h>
#include <storage/storage.h>
#include <string.h>

typedef struct {
  uint8_t command, origin;
  uint32_t at, epoch, revision;
} Command;
typedef struct {
  NpBleEvents ble;
  NpProfile *profile;
  FuriThread *worker;
  ViewPort *view;
  NpParser parser;
  NpFrame incoming, outgoing;
  uint8_t wire[788];
  uint8_t last_type;
  uint32_t last_payload_crc;
  uint32_t session, last_id, tx_id, last_peer, last_request, error_at,
      error_count;
  atomic_bool exit, reset, ready;
  FuriMutex *lock;
  NpModel model;
  NpInput keys;
  FuriMessageQueue *commands;
  char status[40], overlay[32];
  uint32_t overlay_until;
  uint32_t outstanding_id[4], outstanding_at[4];
} App;
static bool send_frame(App *a, uint8_t type, const uint8_t *p,
                       uint16_t length) {
  if (a->tx_id == UINT32_MAX)
    return false;
  NpFrame *f = &a->outgoing;
  f->type = type;
  f->session = a->session;
  f->id = ++a->tx_id;
  f->length = length;
  if (length)
    memcpy(f->payload, p, length);
  size_t n = np_encode(f, a->wire);
  for (size_t pos = 0; pos < n; pos += 20) {
    if (atomic_load(&a->ble.stopping) || !atomic_load(&a->ble.subscribed))
      return false;
    atomic_store(&a->ble.confirmed, false);
    if (!np_ble_tx_submit(a->profile, a->wire + pos, MIN((size_t)20, n - pos)))
      return false;
    uint32_t start = furi_get_tick();
    while (!atomic_load(&a->ble.confirmed)) {
      if (atomic_load(&a->ble.stopping) || atomic_load(&a->ble.disconnected) ||
          (uint32_t)(furi_get_tick() - start) > 5000)
        return false;
      furi_delay_ms(5);
    }
  }
  return n > 0;
}
static void message(App *a, const char *text) {
  furi_mutex_acquire(a->lock, FuriWaitForever);
  snprintf(a->overlay, sizeof(a->overlay), "%s", text);
  a->overlay_until = furi_get_tick() + 1200;
  furi_mutex_release(a->lock);
  view_port_update(a->view);
}
static void receive(const NpFrame *f, void *ctx) {
  App *a = ctx;
  uint32_t now = furi_get_tick();
  if (atomic_load(&a->reset) || atomic_load(&a->ble.stopping))
    return;
  if (!a->session && f->type == 1 && f->id == 1) {
    a->session = f->session;
    a->last_id = 1;
    a->last_peer = now;
    a->last_type = f->type;
    a->last_payload_crc = np_crc(f->payload, f->length);
    uint8_t ack[12] = {1, 0, 0, 3, 128, 0, 0, 0, 1, 0, 0, 0};
    if (f->payload[0] != 1) {
      ack[0] = 0;
      ack[1] = 1;
    }
    if (!send_frame(a, 2, ack, 12) || ack[1])
      atomic_store(&a->reset, true);
    furi_mutex_acquire(a->lock, FuriWaitForever);
    snprintf(a->status, sizeof(a->status), "Reading player...");
    furi_mutex_release(a->lock);
    return;
  }
  if (a->session && f->session == a->session && f->id == a->last_id &&
      f->type == a->last_type &&
      np_crc(f->payload, f->length) == a->last_payload_crc) {
    if (f->type == 1 && !atomic_load(&a->ready)) {
      const uint8_t ack[12] = {1, 0, 0, 3, 128, 0, 0, 0, 1, 0, 0, 0};
      if (!send_frame(a, 2, ack, 12))
        atomic_store(&a->reset, true);
      return;
    }
    if (f->type == 16) {
      uint8_t ack[8];
      np_put(ack, f->id, 4);
      np_put(ack + 4, np_u32(f->payload + 8), 4);
      if (!send_frame(a, 18, ack, 8))
        atomic_store(&a->reset, true);
      return;
    }
  }
  if (!a->session || f->session != a->session || f->id <= a->last_id) {
    atomic_store(&a->reset, true);
    return;
  }
  a->last_id = f->id;
  a->last_type = f->type;
  a->last_payload_crc = np_crc(f->payload, f->length);
  switch (f->type) {
  case 16:
  case 17: {
    if (f->type == 17 && !atomic_load(&a->ready)) {
      atomic_store(&a->reset, true);
      return;
    }
    furi_mutex_acquire(a->lock, FuriWaitForever);
    if (atomic_load(&a->ble.stopping) || atomic_load(&a->reset)) {
      furi_mutex_release(a->lock);
      return;
    }
    int applied = np_model_apply(&a->model, f, now);
    furi_mutex_release(a->lock);
    if (applied < 0) {
      atomic_store(&a->ready, false);
      if (now - a->last_request >= 1000) {
        a->last_request = now;
        if (!send_frame(a, 0x30, NULL, 0))
          atomic_store(&a->reset, true);
      }
    } else if (f->type == 16) {
      uint8_t ack[8];
      np_put(ack, f->id, 4);
      np_put(ack + 4, np_u32(f->payload + 8), 4);
      if (send_frame(a, 18, ack, 8) && !atomic_load(&a->ble.stopping) &&
          !atomic_load(&a->reset) && !atomic_load(&a->ble.disconnected))
        atomic_store(&a->ready, true);
      else
        atomic_store(&a->reset, true);
    }
    break;
  }
  case 0x40:
    if (!send_frame(a, 0x41, f->payload, 4))
      atomic_store(&a->reset, true);
    break;
  case 0x21:
    for (int i = 0; i < 4; i++)
      if (a->outstanding_id[i] == np_u32(f->payload))
        a->outstanding_id[i] = 0;
    if (f->payload[4]) {
      const char *errors[] = {"",
                              "No player",
                              "Player changed",
                              "Unsupported",
                              "Command expired",
                              "Phone needs access",
                              "Command failed",
                              "Not ready"};
      message(a, errors[f->payload[4]]);
    }
    break;
  case 0x7e:
    message(a, "Protocol error");
    atomic_store(&a->reset, true);
    break;
  case 0x7f:
    atomic_store(&a->reset, true);
    break;
  default:
    atomic_store(&a->reset, true);
    return;
  }
  a->last_peer = now;
  view_port_update(a->view);
}
static void command_send(App *a, Command *c, uint32_t now) {
  uint32_t ttl = c->origin ? 300 : 750, age = now - c->at;
  if (age >= ttl || !atomic_load(&a->ready))
    return;
  furi_mutex_acquire(a->lock, FuriWaitForever);
  bool valid = a->model.fresh && a->model.epoch == c->epoch;
  furi_mutex_release(a->lock);
  if (!valid)
    return;
  uint8_t p[12];
  np_put(p, c->epoch, 4);
  np_put(p + 4, c->revision, 4);
  p[8] = c->command;
  p[9] = c->origin;
  np_put(p + 10, ttl - age, 2);
  if (!send_frame(a, 32, p, 12)) {
    atomic_store(&a->reset, true);
    return;
  }
  int slot = -1;
  for (int i = 0; i < 4; i++)
    if (!a->outstanding_id[i]) {
      slot = i;
      break;
    }
  if (slot < 0) {
    message(a, "No response");
    return;
  }
  a->outstanding_id[slot] = a->tx_id;
  a->outstanding_at[slot] = now;
}
static int32_t worker(void *ctx) {
  App *a = ctx;
  NpRx r;
  Command command;
  a->error_at = furi_get_tick();
  a->error_count = a->parser.errors;
  while (!atomic_load(&a->ble.stopping)) {
    if (furi_message_queue_get(a->ble.rx, &r, 20) == FuriStatusOk)
      np_feed(&a->parser, r.bytes, r.length, furi_get_tick(), &a->incoming,
              receive, a);
    uint32_t now = furi_get_tick();
    if (atomic_load(&a->reset) || atomic_load(&a->ble.disconnected)) {
      atomic_store(&a->ready, false);
      continue;
    }
    if (np_parser_expire(&a->parser, now) || atomic_load(&a->ble.overflow) ||
        (a->session && now - a->last_peer >= 35000))
      atomic_store(&a->reset, true);
    if (now - a->error_at >= 10000) {
      a->error_at = now;
      a->error_count = a->parser.errors;
    }
    if (a->parser.errors - a->error_count >= 3)
      atomic_store(&a->reset, true);
    if (!atomic_load(&a->reset) &&
        furi_message_queue_get(a->commands, &command, 0) == FuriStatusOk)
      command_send(a, &command, now);
    for (int i = 0; i < 4; i++)
      if (a->outstanding_id[i] && now - a->outstanding_at[i] >= 2000) {
        a->outstanding_id[i] = 0;
        message(a, "No response");
      }
  }
  return 0;
}
static bool can_control(App *a) {
  return !atomic_load(&a->ble.stopping) && atomic_load(&a->ready) &&
         a->model.fresh && a->model.epoch &&
         !atomic_load(&a->ble.disconnected) && !atomic_load(&a->reset);
}
static void queue_command(App *a, int command, bool repeat, uint32_t now) {
  if (!(a->model.caps & (1u << (command - 1))))
    return;
  Command c = {(uint8_t)command, (uint8_t)repeat, now, a->model.epoch,
               a->model.revision};
  if (furi_message_queue_put(a->commands, &c, 0) != FuriStatusOk) {
    snprintf(a->overlay, sizeof(a->overlay), "Command queue full");
    a->overlay_until = now + 1200;
  }
}
static void draw(Canvas *c, void *ctx) {
  App *a = ctx;
  furi_mutex_acquire(a->lock, FuriWaitForever);
  uint32_t now = furi_get_tick();
  if ((int32_t)(now - a->overlay_until) >= 0)
    a->overlay[0] = 0;
  np_view_draw(c, &a->model, now, a->status, a->overlay, a->keys.held);
  furi_mutex_release(a->lock);
}
static void input(InputEvent *e, void *ctx) {
  App *a = ctx;
  int key = -1, event = -1;
  switch (e->key) {
  case InputKeyUp:
    key = 0;
    break;
  case InputKeyDown:
    key = 1;
    break;
  case InputKeyLeft:
    key = 2;
    break;
  case InputKeyRight:
    key = 3;
    break;
  case InputKeyOk:
    key = 4;
    break;
  case InputKeyBack:
    key = 5;
    break;
  default:
    return;
  }
  switch (e->type) {
  case InputTypePress:
    event = 0;
    break;
  case InputTypeRelease:
    event = 1;
    break;
  case InputTypeShort:
    event = 2;
    break;
  case InputTypeLong:
    event = 3;
    break;
  default:
    return;
  }
  furi_mutex_acquire(a->lock, FuriWaitForever);
  uint32_t now = furi_get_tick();
  int command = np_input(&a->keys, key, event, now, can_control(a));
  if (command < 0)
    atomic_store(&a->exit, true);
  else if (command > 0)
    queue_command(a, command, false, now);
  if (key == 5 && event == 2) {
    snprintf(a->overlay, sizeof(a->overlay), "Hold Back to exit");
    a->overlay_until = now + 1200;
  }
  furi_mutex_release(a->lock);
  view_port_update(a->view);
}
static bool storage_ready(Storage *storage) {
  FuriString *path = furi_string_alloc_set(APP_DATA_PATH(".write-probe"));
  storage_common_resolve_path_and_ensure_app_directory(storage, path);
  File *file = storage_file_alloc(storage);
  bool ok = storage_file_open(file, furi_string_get_cstr(path), FSAM_WRITE,
                              FSOM_CREATE_ALWAYS);
  if (ok)
    storage_file_close(file);
  storage_file_free(file);
  if (ok)
    storage_common_remove(storage, furi_string_get_cstr(path));
  furi_string_free(path);
  return ok;
}
int32_t now_playing_app(void *ctx) {
  UNUSED(ctx);
  App *a = malloc(sizeof(*a));
  if (!a)
    return 1;
  memset(a, 0, sizeof(*a));
  a->lock = furi_mutex_alloc(FuriMutexTypeNormal);
  a->keys.repeat = -1;
  snprintf(a->status, sizeof(a->status), "Waiting for phone");
  a->commands = furi_message_queue_alloc(4, sizeof(Command));
  a->ble.rx = furi_message_queue_alloc(8, sizeof(NpRx));
  a->view = view_port_alloc();
  Gui *gui = furi_record_open(RECORD_GUI);
  Bt *bt = furi_record_open(RECORD_BT);
  Storage *storage = furi_record_open(RECORD_STORAGE);
  view_port_draw_callback_set(a->view, draw, a);
  view_port_input_callback_set(a->view, input, a);
  gui_add_view_port(gui, a->view, GuiLayerFullscreen);
  bool changed = false;
  if (storage_common_stat(storage, EXT_PATH(""), NULL) == FSE_OK &&
      storage_ready(storage) && furi_hal_bt_is_gatt_gap_supported()) {
    bt_disconnect(bt);
    furi_delay_ms(200);
    bt_keys_storage_set_storage_path(bt, APP_DATA_PATH("bt.keys"));
    changed = true;
    a->profile =
        (NpProfile *)bt_profile_start(bt, &np_profile_template, &a->ble);
    if (np_ble_valid(a->profile)) {
      a->worker = furi_thread_alloc_ex("NpTransport", 4096, worker, a);
      furi_thread_start(a->worker);
      furi_hal_bt_start_advertising();
    }
  }
  if (!a->worker) {
    furi_mutex_acquire(a->lock, FuriWaitForever);
    snprintf(a->status, sizeof(a->status), "Check SD / Bluetooth");
    furi_mutex_release(a->lock);
    view_port_update(a->view);
  }
  uint32_t last_draw = 0;
  while (!atomic_load(&a->exit)) {
    if (atomic_load(&a->reset) || atomic_load(&a->ble.disconnected)) {
      atomic_store(&a->ble.stopping, true);
      atomic_store(&a->ready, false);
      furi_mutex_acquire(a->lock, FuriWaitForever);
      np_freeze(&a->model, furi_get_tick());
      a->model.seq = 0;
      memset(&a->keys, 0, sizeof(a->keys));
      a->keys.repeat = -1;
      furi_mutex_release(a->lock);
      view_port_update(a->view);
      atomic_store(&a->ble.stopping, true);
      bt_disconnect(bt);
      if (a->worker) {
        furi_thread_join(a->worker);
        furi_thread_free(a->worker);
        a->worker = NULL;
      }
      atomic_store(&a->ready,
                   false); /* No old worker can enable it after join. */
      a->session = a->last_id = a->tx_id = 0;
      furi_message_queue_reset(a->commands);
      memset(a->outstanding_id, 0, sizeof(a->outstanding_id));
      memset(&a->parser, 0, sizeof(a->parser));
      furi_message_queue_reset(a->ble.rx);
      atomic_store(&a->ble.overflow, false);
      atomic_store(&a->reset, false);
      atomic_store(&a->ble.disconnected, false);
      atomic_store(&a->ble.stopping, false);
      if (np_ble_valid(a->profile)) {
        a->worker = furi_thread_alloc_ex("NpTransport", 4096, worker, a);
        furi_thread_start(a->worker);
        furi_hal_bt_start_advertising();
      }
    }
    furi_mutex_acquire(a->lock, FuriWaitForever);
    uint32_t now = furi_get_tick();
    int repeat = np_input_tick(&a->keys, now, can_control(a));
    if (repeat > 0)
      queue_command(a, repeat, true, now);
    bool animate = a->keys.held || a->overlay[0] ||
                   strlen(a->model.title) > 13 ||
                   strlen(a->model.artist) > 20 || strlen(a->model.album) > 23;
    bool refresh = animate ? (now - last_draw >= 100)
                           : (a->model.state == 3 && a->model.fresh &&
                              now - last_draw >= 1000);
    furi_mutex_release(a->lock);
    if (refresh) {
      last_draw = now;
      view_port_update(a->view);
    }
    furi_delay_ms(25);
  }
  atomic_store(&a->ble.stopping, true);
  atomic_store(&a->ready, false);
  furi_mutex_acquire(a->lock, FuriWaitForever);
  a->model.synced = false;
  snprintf(a->status, sizeof(a->status), "Closing...");
  furi_mutex_release(a->lock);
  view_port_update(a->view);
  if (changed) {
    bt_disconnect(bt);
    furi_delay_ms(200);
  }
  if (a->worker) {
    furi_thread_join(a->worker);
    furi_thread_free(a->worker);
  }
  if (changed) {
    bt_keys_storage_set_default_path(bt);
    while (!bt_profile_restore_default(bt)) {
      furi_mutex_acquire(a->lock, FuriWaitForever);
      snprintf(a->status, sizeof(a->status), "Restore failed; retrying");
      furi_mutex_release(a->lock);
      view_port_update(a->view);
      furi_delay_ms(1000);
    }
  }
  gui_remove_view_port(gui, a->view);
  view_port_free(a->view);
  furi_message_queue_free(a->ble.rx);
  furi_message_queue_free(a->commands);
  furi_mutex_free(a->lock);
  furi_record_close(RECORD_STORAGE);
  furi_record_close(RECORD_BT);
  furi_record_close(RECORD_GUI);
  free(a);
  return 0;
}
