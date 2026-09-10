#include "np_protocol.h"
#include <string.h>
uint16_t np_u16(const uint8_t *p) {
  return (uint16_t)(p[0] | ((uint16_t)p[1] << 8));
}
uint32_t np_u32(const uint8_t *p) {
  return (uint32_t)np_u16(p) | ((uint32_t)np_u16(p + 2) << 16);
}
uint64_t np_u64(const uint8_t *p) {
  return (uint64_t)np_u32(p) | ((uint64_t)np_u32(p + 4) << 32);
}
void np_put(uint8_t *p, uint64_t v, size_t n) {
  for (size_t i = 0; i < n; i++) {
    p[i] = (uint8_t)v;
    v >>= 8;
  }
}
uint32_t np_crc(const uint8_t *p, size_t n) {
  uint32_t c = UINT32_MAX;
  while (n--) {
    c ^= *p++;
    for (int b = 0; b < 8; b++)
      c = (c >> 1) ^ ((c & 1) ? UINT32_C(0xedb88320) : 0);
  }
  return ~c;
}
static bool state(const uint8_t *p, size_t n) {
  if (n < 36)
    return false;
  uint8_t s = p[12], f = p[13];
  int16_t speed = (int16_t)np_u16(p + 14);
  uint64_t pos = np_u64(p + 16), dur = np_u64(p + 24);
  uint16_t caps = np_u16(p + 32);
  if (!np_u32(p + 8) || s > 6 || (f & ~7) || (caps & ~31) || p[34] > 1 ||
      p[35] || speed < -8000 || speed > 8000 || (s != 3 && speed))
    return false;
  if (pos > NP_MAX_TIME || dur > NP_MAX_TIME || (!(f & 1) && pos) ||
      (!(f & 2) && dur) || ((f & 2) && !dur) || ((f & 3) == 3 && pos > dur))
    return false;
  return s == 0 ? !(np_u32(p) || np_u32(p + 4) || f || caps)
                : (np_u32(p) && np_u32(p + 4));
}
bool np_validate(uint8_t t, const uint8_t *p, size_t n) {
  if (n > 768)
    return false;
  switch (t) {
  case 1:
  case 2:
    return n == 12 && np_u16(p + 2) == 768 && np_u16(p + 4) >= 20 &&
           np_u16(p + 4) <= 128 && !np_u16(p + 6) && np_u32(p + 8) == 1 &&
           (t == 1 ? (p[0] >= 1 && p[0] <= p[1])
                   : ((p[0] >= 1 && p[0] <= 2 && p[1] == 0) ||
                      (p[0] == 0 && p[1] == 1)));
  case 0x10: {
    if (n < 44 || !state(p, n))
      return false;
    const unsigned max[4] = {192, 128, 192, 48};
    size_t sum = 44;
    for (int i = 0; i < 4; i++) {
      unsigned l = np_u16(p + 36 + 2 * i);
      if (l > max[i] || (!p[12] && i < 3 && l))
        return false;
      sum += l;
    }
    if (sum != n)
      return false;
    for (size_t i = 44; i < n; i++)
      if (p[i] < 32 || p[i] > 126)
        return false;
    return true;
  }
  case 0x11:
    return n == 36 && state(p, n);
  case 0x12:
    return n == 8 && np_u32(p) && np_u32(p + 4);
  case 0x13:
    if ((n != 12 && n != 282) || !np_u32(p) || !np_u32(p + 4) || p[11])
      return false;
    if (n == 12)
      return !p[8] && !p[9] && !p[10];
    if (p[8] != 45 || p[9] != 45 || p[10] != 1)
      return false;
    for (size_t i = 17; i < n; i += 6)
      if (p[i] & 0xe0)
        return false;
    return true;
  case 0x20:
    return n == 12 && np_u32(p) && np_u32(p + 4) && p[8] >= 1 && p[8] <= 5 &&
           p[9] <= 1 && (!p[9] || p[8] >= 4) && np_u16(p + 10) >= 1 &&
           np_u16(p + 10) <= 750;
  case 0x21:
    return n == 12 && np_u32(p) && p[4] <= 7 && !p[5] && !np_u16(p + 6);
  case 0x30:
    return n == 0;
  case 0x40:
  case 0x41:
    return n == 4;
  case 0x7e:
    return n == 8 && np_u16(p + 4) >= 1 && np_u16(p + 4) <= 6 && !np_u16(p + 6);
  case 0x7f:
    return n == 1 && p[0] >= 1 && p[0] <= 3;
  default:
    return false;
  }
}
bool np_decode(const uint8_t *b, size_t n, NpFrame *f) {
  if (n < 20 || n > 788 || memcmp(b, "FNP1", 4) || b[4] != 1)
    return false;
  size_t l = np_u16(b + 6);
  if (l > 768 || n != 20 + l || !np_u32(b + 8) || !np_u32(b + 12) ||
      np_crc(b + 4, 12 + l) != np_u32(b + 16 + l) ||
      !np_validate(b[5], b + 16, l))
    return false;
  f->type = b[5];
  f->session = np_u32(b + 8);
  f->id = np_u32(b + 12);
  f->length = (uint16_t)l;
  memcpy(f->payload, b + 16, l);
  return true;
}
size_t np_encode(const NpFrame *f, uint8_t *b) {
  if (!f->session || !f->id || !np_validate(f->type, f->payload, f->length))
    return 0;
  memcpy(b, "FNP1", 4);
  b[4] = 1;
  b[5] = f->type;
  np_put(b + 6, f->length, 2);
  np_put(b + 8, f->session, 4);
  np_put(b + 12, f->id, 4);
  memcpy(b + 16, f->payload, f->length);
  np_put(b + 16 + f->length, np_crc(b + 4, 12 + f->length), 4);
  return 20 + f->length;
}
bool np_parser_expire(NpParser *p, uint32_t now) {
  if (p->used && ((uint32_t)(now - p->last) >= 5000 ||
                  (uint32_t)(now - p->first) >= 15000)) {
    p->used = 0;
    p->errors++;
    return true;
  }
  return false;
}
static void discard(NpParser *p, size_t n) {
  p->used -= n;
  memmove(p->bytes, p->bytes + n, p->used);
}
void np_feed(NpParser *p, const uint8_t *bytes, size_t n, uint32_t now,
             NpFrame *scratch, NpReceive cb, void *ctx) {
  np_parser_expire(p, now);
  for (size_t i = 0; i < n; i++) {
    if (!p->used)
      p->first = now;
    p->last = now;
    if (p->used == 788) {
      discard(p, 1);
      p->errors++;
    }
    p->bytes[p->used++] = bytes[i];
    while (p->used >= 4) {
      if (memcmp(p->bytes, "FNP1", 4)) {
        discard(p, 1);
        continue;
      }
      if (p->used < 16)
        break;
      size_t l = np_u16(p->bytes + 6);
      if (p->bytes[4] != 1 || l > 768) {
        discard(p, 1);
        p->errors++;
        continue;
      }
      if (p->used < 20 + l)
        break;
      if (np_decode(p->bytes, 20 + l, scratch)) {
        discard(p, 20 + l);
        cb(scratch, ctx);
      } else {
        discard(p, 1);
        p->errors++;
      }
    }
  }
}
