#pragma once
#include <stdbool.h>
/* Official 1.4.3 gatt.c returns result != BLE_STATUS_SUCCESS. */
static inline bool np_ble_update_ok(bool sdk_error) { return !sdk_error; }
