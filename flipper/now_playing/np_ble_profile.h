#pragma once
#include <furi.h>
#include <furi_ble/profile_interface.h>
#include <stdatomic.h>
typedef struct {
  uint16_t length;
  uint8_t bytes[128];
} NpRx;
typedef struct {
  FuriMessageQueue *rx;
  atomic_bool stopping, overflow, subscribed, confirmed, disconnected;
} NpBleEvents;
typedef struct NpProfile NpProfile;
extern const FuriHalBleProfileTemplate np_profile_template;
bool np_ble_tx_submit(NpProfile *profile, const uint8_t *bytes,
                      uint16_t length);
bool np_ble_valid(const NpProfile *profile);
#include "core/np_ble_adapter.h"
