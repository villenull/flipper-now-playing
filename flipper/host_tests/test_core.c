#include "np_ble_adapter.h"
#include "np_model.h"
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
static unsigned count;
static void receive(const NpFrame *f, void *ctx) {
  (void)ctx;
  assert(f->session);
  count++;
}
int main(int argc, char **argv) {
  assert(np_ble_update_ok(false));
  assert(!np_ble_update_ok(true));
  assert(argc == 2);
  FILE *file = fopen(argv[1], "r");
  assert(file);
  char hex[1600];
  uint8_t bytes[788], out[788];
  NpFrame f;
  unsigned vectors = 0;
  assert(np_crc((const uint8_t *)"123456789", 9) == 0xcbf43926);
  while (fgets(hex, sizeof(hex), file)) {
    size_t n = strcspn(hex, "\r\n") / 2;
    for (size_t i = 0; i < n; i++) {
      unsigned v;
      assert(sscanf(hex + 2 * i, "%2x", &v) == 1);
      bytes[i] = (uint8_t)v;
    }
    assert(np_decode(bytes, n, &f));
    if (f.type == 16) {
      NpModel model = {0};
      assert(np_model_apply(&model, &f, 100) == 1);
      if (!strcmp(model.title, "Get Lucky")) {
        assert(model.epoch == 1 && model.revision == 1 && model.seq == 1);
        assert(model.position == 102000 && model.duration == 248000 &&
               model.speed == 1000 && model.caps == 31);
        assert(!strcmp(model.artist, "Daft Punk") &&
               !strcmp(model.album, "Random Access Memories"));
      }
      NpModel saved = model;
      assert(np_model_apply(&model, &f, 1000) == 0);
      assert(!memcmp(&saved, &model, sizeof(model)));
      NpFrame update = f;
      update.type = 17;
      update.length = 36;
      np_put(update.payload + 8, model.seq + 1, 4);
      np_put(update.payload + 4, model.revision + 1, 4);
      if (model.epoch) {
        assert(np_model_apply(&model, &update, 1001) == -1);
        assert(!memcmp(&saved, &model, sizeof(model)));
      }
      update = f;
      update.payload[35] = 1;
      assert(np_model_apply(&model, &update, 2000) == -1);
      assert(!memcmp(&saved, &model, sizeof(model)));
    }
    assert(np_encode(&f, out) == n);
    assert(!memcmp(out, bytes, n));
    vectors++;
    for (size_t split = 0; split <= n; split++) {
      NpParser p = {0};
      count = 0;
      np_feed(&p, bytes, split, 0, &f, receive, NULL);
      np_feed(&p, bytes + split, n - split, 1, &f, receive, NULL);
      assert(count == 1);
      assert(p.used == 0);
    }
    NpParser p = {0};
    count = 0;
    for (size_t i = 0; i < n; i++)
      np_feed(&p, bytes + i, 1, 0, &f, receive, NULL);
    assert(count == 1);
    for (size_t i = 0; i < n; i++)
      for (unsigned bit = 0; bit < 8; bit++) {
        bytes[i] ^= 1u << bit;
        assert(!np_decode(bytes, n, &f));
        bytes[i] ^= 1u << bit;
      }
    np_feed(&p, bytes, 10, 0, &f, receive, NULL);
    assert(np_parser_expire(&p, 5000));
    assert(!p.used);
  }
  fclose(file);
  assert(vectors == 22);
  NpParser p = {0};
  srand(1123);
  for (unsigned i = 0; i < 200000; i++) {
    uint8_t b = (uint8_t)rand();
    np_feed(&p, &b, 1, i, &f, receive, NULL);
    assert(p.used <= 788);
  }
  NpModel m = {.epoch = 1,
               .revision = 1,
               .seq = 1,
               .state = 3,
               .flags = 3,
               .speed = 1000,
               .position = 1000,
               .duration = 10000,
               .anchor = UINT32_MAX - 499,
               .fresh = true,
               .synced = true};
  assert(np_position(&m, 500) == 2000);
  np_freeze(&m, 500);
  assert(np_position(&m, 5000) == 2000);
  m.fresh = true;
  m.anchor = 0;
  m.speed = -8000;
  assert(np_position(&m, 1000) == 0);
  m.speed = 8000;
  assert(np_position(&m, 10000) == 10000);
  char label[24];
  np_time(label, sizeof(label), 61001, true, true);
  assert(!strcmp(label, "-1:02"));
  np_time(label, sizeof(label), 3661000, true, false);
  assert(!strcmp(label, "1:01:01"));
  np_time(label, sizeof(label), 0, false, false);
  assert(!strcmp(label, "--:--"));
  assert(np_scroll_offset(24, 0) == 0);
  assert(np_scroll_offset(24, 4999) == 0);
  assert(np_scroll_offset(24, 5000) == 0);
  assert(np_scroll_offset(24, 6000) == 12);
  assert(np_scroll_offset(24, 7000) == 24);
  assert(np_scroll_offset(24, 8499) == 24);
  assert(np_scroll_offset(24, 8500) == 0);
  assert(np_scroll_offset(0, UINT32_MAX) == 0);
  NpFrame art = {.type = 19, .length = 282};
  np_put(art.payload, 1, 4);
  np_put(art.payload + 4, 1, 4);
  art.payload[8] = art.payload[9] = 45;
  art.payload[10] = 1;
  art.payload[12] = 1;
  assert(np_artwork_apply(&m, &art) == 1 && m.has_artwork && m.artwork[0] == 1);
  NpModel art_saved = m;
  art.payload[17] = 128;
  assert(np_artwork_apply(&m, &art) == -1 &&
         !memcmp(&m, &art_saved, sizeof(m)));
  art.payload[17] = 0;
  np_put(art.payload + 4, 2, 4);
  assert(np_artwork_apply(&m, &art) == 0 && !memcmp(&m, &art_saved, sizeof(m)));
  np_put(art.payload + 4, 1, 4);
  art.length = 12;
  art.payload[8] = art.payload[9] = art.payload[10] = 0;
  assert(np_artwork_apply(&m, &art) == 1 && !m.has_artwork && !m.artwork[0]);
  m.fresh = false;
  art.length = 282;
  art.payload[8] = art.payload[9] = 45;
  art.payload[10] = 1;
  assert(np_artwork_apply(&m, &art) == 0 && !m.has_artwork);
  m = art_saved;
  NpFrame changed_track = {.type = 16, .length = 44};
  np_put(changed_track.payload, 1, 4);
  np_put(changed_track.payload + 4, 2, 4);
  np_put(changed_track.payload + 8, 2, 4);
  changed_track.payload[12] = 2;
  assert(np_model_apply(&m, &changed_track, 9000) == 1);
  assert(!m.has_artwork && !m.artwork[0] && m.scroll_anchor == 9000);
  m.has_artwork = true;
  m.artwork[0] = 1;
  np_put(changed_track.payload + 8, 3, 4);
  assert(np_model_apply(&m, &changed_track, 10000) == 1);
  assert(m.has_artwork && m.artwork[0] == 1 && m.scroll_anchor == 9000);
  NpInput input = {.repeat = -1};
  assert(np_input(&input, 4, 0, 0, true) == 1);
  assert(np_input(&input, 4, 0, 0, true) == 0);
  assert(np_input(&input, 4, 2, 1, true) == 0);
  np_input(&input, 4, 1, 2, true);
  assert(np_input(&input, 4, 0, 3, true) == 1);
  assert(np_input(&input, 0, 0, 100, true) == 4);
  assert(np_input_tick(&input, 499, true) == 0);
  assert(np_input_tick(&input, 500, true) == 4);
  assert(np_input_tick(&input, 500, true) == 0);
  assert(np_input_tick(&input, 5000, true) == 4);
  assert(np_input_tick(&input, 5000, true) == 0);
  assert(np_input(&input, 1, 0, 5100, true) == 5);
  assert(np_input_tick(&input, 5500, true) == 5);
  np_input(&input, 1, 1, 5501, true);
  assert(np_input_tick(&input, 6000, true) == 0);
  np_input(&input, 0, 1, 6001, true);
  np_input(&input, 0, 0, 6002, true);
  assert(np_input_tick(&input, 16002, true) == 0);
  assert(np_input(&input, 5, 3, 0, false) == -1);
  assert(np_input(&input, 5, 2, 0, true) == 0);
  assert(np_input_tick(&input, 0, false) == 0);
  puts("PASS: C vectors, every split, bit corruption, bounded noise, time/tick "
       "wrap, input transitions and repeat limits");
  return 0;
}
