#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#define NP_MAX_PAYLOAD 768
#define NP_MAX_FRAME 788
#define NP_MAX_TIME UINT64_C(315360000000)
typedef struct {
  uint8_t type;
  uint32_t session, id;
  uint16_t length;
  uint8_t payload[NP_MAX_PAYLOAD];
} NpFrame;
typedef struct {
  uint8_t bytes[NP_MAX_FRAME];
  size_t used;
  uint32_t first, last, errors;
} NpParser;
typedef void (*NpReceive)(const NpFrame *, void *);
uint16_t np_u16(const uint8_t *p);
uint32_t np_u32(const uint8_t *p);
uint64_t np_u64(const uint8_t *p);
void np_put(uint8_t *p, uint64_t v, size_t n);
uint32_t np_crc(const uint8_t *p, size_t n);
bool np_validate(uint8_t type, const uint8_t *p, size_t n);
bool np_decode(const uint8_t *bytes, size_t n, NpFrame *out);
size_t np_encode(const NpFrame *frame, uint8_t *out);
void np_feed(NpParser *parser, const uint8_t *bytes, size_t n, uint32_t now,
             NpFrame *scratch, NpReceive cb, void *ctx);
bool np_parser_expire(NpParser *parser, uint32_t now);
