#pragma once
#include "np_protocol.h"
typedef struct {
  uint32_t epoch, revision, seq, anchor, scroll_anchor;
  uint8_t state, flags, mode;
  int16_t speed;
  uint16_t caps;
  uint64_t position, duration;
  char title[193], artist[129], album[193], app[49];
  bool synced, fresh;
} NpModel;
/* 1 applied, 0 old/duplicate, -1 mismatched STATE; syntax is validated first.
 */
int np_model_apply(NpModel *m, const NpFrame *f, uint32_t now);
uint64_t np_position(const NpModel *m, uint32_t now);
void np_freeze(NpModel *m, uint32_t now);
void np_time(char *out, size_t n, uint64_t ms, bool valid, bool remaining);
typedef struct {
  uint8_t held;
  int repeat;
  uint32_t pressed, next;
} NpInput;
/* key: 0 up, 1 down, 2 left, 3 right, 4 OK, 5 Back; event: 0 press,1 release,2
 * short,3 long,4 repeat */
int np_input(NpInput *input, int key, int event, uint32_t now, bool ready);
int np_input_tick(NpInput *input, uint32_t now, bool ready);
