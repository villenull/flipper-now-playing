#include "np_ble_profile.h"
#include <app_common.h>
#include <ble/ble.h>
#include <furi_ble/event_dispatcher.h>
#include <furi_ble/gatt.h>
#include <furi_hal.h>
#include <services/battery_service.h>
#include <services/dev_info_service.h>
#include <stm32wbxx.h>
#include <string.h>

struct NpProfile {
  FuriHalBleProfileBase base;
  NpBleEvents *events;
  uint16_t service;
  BleGattCharacteristicInstance rx, tx;
  GapSvcEventHandler *handler;
  atomic_uint callbacks;
  BleServiceBattery *battery;
  BleServiceDevInfo *info;
};
_Static_assert(offsetof(NpProfile, base) == 0, "profile ABI");
static const uint8_t uuid[16] = {0x80, 0x7f, 0x6e, 0x5d, 0x4c, 0x3b,
                                 0xe6, 0xa6, 0x1b, 0x4f, 0x8a, 0x6d,
                                 0x00, 0x10, 0x9a, 0x8f};
typedef struct {
  const uint8_t *bytes;
  uint16_t length;
} Value;
static bool value(const void *ctx, const uint8_t **data, uint16_t *length) {
  if (!data) {
    *length = 128;
    return false;
  }
  const Value *v = ctx;
  *data = v ? v->bytes : NULL;
  *length = v ? v->length : 0;
  return false;
}
static BleEventAckStatus event_inner(void *raw, void *ctx) {
  NpProfile *p = ctx;
  NpBleEvents *e = p->events;
  hci_event_pckt *h = (hci_event_pckt *)((hci_uart_pckt *)raw)->data;
  if (h->evt == HCI_DISCONNECTION_COMPLETE_EVT_CODE) {
    atomic_store(&e->disconnected, true);
    atomic_store(&e->subscribed, false);
    return BleEventNotAck;
  }
  if (h->evt != HCI_VENDOR_SPECIFIC_DEBUG_EVT_CODE || atomic_load(&e->stopping))
    return BleEventNotAck;
  evt_blecore_aci *v = (evt_blecore_aci *)h->data;
  if (v->ecode == ACI_GATT_SERVER_CONFIRMATION_VSEVT_CODE) {
    atomic_store(&e->confirmed, true);
    return BleEventAckFlowEnable;
  }
  if (v->ecode != ACI_GATT_ATTRIBUTE_MODIFIED_VSEVT_CODE)
    return BleEventNotAck;
  aci_gatt_attribute_modified_event_rp0 *a =
      (aci_gatt_attribute_modified_event_rp0 *)v->data;
  if (a->Attr_Handle == p->tx.handle + 2) {
    bool enabled = a->Attr_Data_Length == 2 && a->Attr_Data[0] == 2 &&
                   a->Attr_Data[1] == 0;
    atomic_store(&e->subscribed, enabled);
    return BleEventAckFlowEnable;
  }
  if (a->Attr_Handle != p->rx.handle + 1)
    return BleEventNotAck;
  if (a->Attr_Data_Length > 128 || !atomic_load(&e->subscribed)) {
    atomic_store(&e->overflow, true);
    return BleEventAckFlowEnable;
  }
  NpRx r = {.length = a->Attr_Data_Length};
  memcpy(r.bytes, a->Attr_Data, r.length);
  if (furi_message_queue_put(e->rx, &r, 0) != FuriStatusOk)
    atomic_store(&e->overflow, true);
  return BleEventAckFlowEnable;
}
static BleEventAckStatus event(void *raw, void *ctx) {
  NpProfile *p = ctx;
  atomic_fetch_add(&p->callbacks, 1);
  BleEventAckStatus result = event_inner(raw, ctx);
  atomic_fetch_sub(&p->callbacks, 1);
  return result;
}
static FuriHalBleProfileBase *start(FuriHalBleProfileParams ctx) {
  NpProfile *p = malloc(sizeof(*p));
  if (!p)
    return NULL;
  memset(p, 0, sizeof(*p));
  p->base.config = &np_profile_template;
  p->events = ctx;
  Service_UUID_t id;
  memcpy(id.Service_UUID_128, uuid, 16);
  p->battery = ble_svc_battery_start(true);
  p->info = ble_svc_dev_info_start();
  if (ble_gatt_service_add(UUID_TYPE_128, &id, PRIMARY_SERVICE, 8,
                           &p->service)) {
    BleGattCharacteristicParams c = {
        .name = "NP RX",
        .data_prop_type = FlipperGattCharacteristicDataCallback,
        .data.callback = {.fn = value},
        .uuid_type = UUID_TYPE_128,
        .is_variable = 1,
        .char_properties = CHAR_PROP_WRITE,
        .security_permissions = ATTR_PERMISSION_AUTHEN_WRITE,
        .gatt_evt_mask = GATT_NOTIFY_ATTRIBUTE_WRITE};
    memcpy(c.uuid.Char_UUID_128, uuid, 16);
    c.uuid.Char_UUID_128[12] = 1;
    ble_gatt_characteristic_init(p->service, &c, &p->rx);
    c.name = "NP TX";
    c.uuid.Char_UUID_128[12] = 2;
    c.char_properties = CHAR_PROP_INDICATE;
    c.security_permissions = ATTR_PERMISSION_AUTHEN_READ;
    ble_gatt_characteristic_init(p->service, &c, &p->tx);
  }
  p->handler = ble_event_dispatcher_register_svc_handler(event, p);
  return &p->base;
}
static void stop(FuriHalBleProfileBase *base) {
  NpProfile *p = (NpProfile *)base;
  /* Dispatcher invokes handlers only on BleEventWorker, never an interrupt.
   * Freeze thread scheduling during list mutation; callback count fences a
   * previously running handler. No radio call or wait inside the lock. */
  if (p->handler) {
    for (;;) {
      int32_t lock = furi_kernel_lock();
      if (atomic_load(&p->callbacks) == 0) {
        ble_event_dispatcher_unregister_svc_handler(p->handler);
        furi_kernel_restore_lock(lock);
        break;
      }
      furi_kernel_restore_lock(lock);
      furi_delay_tick(1);
    }
  }
  if (p->rx.characteristic)
    ble_gatt_characteristic_delete(p->service, &p->rx);
  if (p->tx.characteristic)
    ble_gatt_characteristic_delete(p->service, &p->tx);
  if (p->service)
    ble_gatt_service_delete(p->service);
  if (p->battery)
    ble_svc_battery_stop(p->battery);
  if (p->info)
    ble_svc_dev_info_stop(p->info);
  free(
      p); /* HAL calls stop but does not free the instance in official 1.4.3. */
}
static void config(GapConfig *c, FuriHalBleProfileParams ctx) {
  UNUSED(ctx);
  memset(c, 0, sizeof(*c));
  c->adv_service.UUID_Type = UUID_TYPE_128;
  memcpy(c->adv_service.Service_UUID_128, uuid, 16);
  c->bonding_mode = true;
  c->pairing_method = GapPairingPinCodeVerifyYesNo;
  c->conn_param.conn_int_min = 6;
  c->conn_param.conn_int_max = 0x24;
  memcpy(c->mac_address, furi_hal_version_get_ble_mac(), 6);
  c->mac_address[2] += 2;
  FuriString *name =
      furi_string_alloc_set(furi_hal_version_get_ble_local_device_name_ptr());
  furi_string_replace_str(name, "Flipper", "NP");
  furi_string_left(name, sizeof(c->adv_name) - 1);
  memcpy(c->adv_name, furi_string_get_cstr(name), furi_string_size(name));
  furi_string_free(name);
}
const FuriHalBleProfileTemplate np_profile_template = {
    .start = start, .stop = stop, .get_gap_config = config};
bool np_ble_valid(const NpProfile *p) {
  return p && p->service && p->rx.handle && p->tx.handle;
}
bool np_ble_tx_submit(NpProfile *p, const uint8_t *bytes, uint16_t length) {
  if (!np_ble_valid(p) || length > 20 || !length)
    return false;
  Value v = {bytes, length};
  return np_ble_update_ok(
      ble_gatt_characteristic_update(p->service, &p->tx, &v));
}
