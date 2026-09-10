#include "np_model.h"
#include <stdio.h>
#include <string.h>
int np_model_apply(NpModel *m, const NpFrame *f, uint32_t now) {
  const uint8_t *p = f->payload;
  if ((f->type != 16 && f->type != 17) || !np_validate(f->type, p, f->length))
    return -1;
  uint32_t seq = np_u32(p + 8), epoch = np_u32(p), rev = np_u32(p + 4);
  if (f->type == 17 && (!m->synced || epoch != m->epoch || rev != m->revision))
    return -1;
  if (m->synced && seq <= m->seq)
    return 0;
  if (f->type == 16) {
    if (!m->synced || epoch != m->epoch || rev != m->revision || !epoch) {
      m->has_artwork = false;
      memset(m->artwork, 0, sizeof(m->artwork));
      m->scroll_anchor = now;
    }
    const size_t lengths[4] = {np_u16(p + 36), np_u16(p + 38), np_u16(p + 40),
                               np_u16(p + 42)};
    char *fields[4] = {m->title, m->artist, m->album, m->app};
    size_t offset = 44;
    bool changed = false;
    for (int i = 0; i < 4; i++) {
      if (strlen(fields[i]) != lengths[i] ||
          memcmp(fields[i], p + offset, lengths[i]))
        changed = true;
      memcpy(fields[i], p + offset, lengths[i]);
      fields[i][lengths[i]] = 0;
      offset += lengths[i];
    }
    if (changed || !m->synced)
      m->scroll_anchor = now;
  }
  m->epoch = epoch;
  m->revision = rev;
  m->seq = seq;
  m->state = p[12];
  m->flags = p[13];
  m->speed = (int16_t)np_u16(p + 14);
  m->position = np_u64(p + 16);
  m->duration = np_u64(p + 24);
  m->caps = np_u16(p + 32);
  m->mode = p[34];
  m->anchor = now;
  m->synced = true;
  m->fresh = true;
  return 1;
}
uint64_t np_position(const NpModel *m, uint32_t now) {
  int64_t pos = (int64_t)m->position;
  if (m->fresh && m->state == 3 && (m->flags & 1))
    pos += (int64_t)(uint32_t)(now - m->anchor) * m->speed / 1000;
  if (pos < 0)
    pos = 0;
  uint64_t cap = (m->flags & 2) ? m->duration : NP_MAX_TIME;
  return (uint64_t)pos > cap ? cap : (uint64_t)pos;
}
void np_freeze(NpModel *m, uint32_t now) {
  m->position = np_position(m, now);
  m->anchor = now;
  m->fresh = false;
}
void np_time(char *out, size_t n, uint64_t ms, bool valid, bool remaining) {
  if (!valid) {
    snprintf(out, n, "--:--");
    return;
  }
  uint32_t s = (uint32_t)((ms + (remaining ? 999 : 0)) / 1000);
  const char *sign = remaining ? "-" : "";
  if (s >= 3600)
    snprintf(out, n, "%s%lu:%02lu:%02lu", sign, (unsigned long)(s / 3600),
             (unsigned long)(s / 60 % 60), (unsigned long)(s % 60));
  else
    snprintf(out, n, "%s%lu:%02lu", sign, (unsigned long)(s / 60),
             (unsigned long)(s % 60));
}
int np_input(NpInput *i, int key, int event, uint32_t now, bool ready) {
  if (key < 0 || key > 5)
    return 0;
  if (key == 5 && event == 3)
    return -1;
  if (!ready) {
    memset(i, 0, sizeof(*i));
    i->repeat = -1;
    return 0;
  }
  if (event == 1) {
    i->held &= ~(1u << key);
    if (i->repeat == key)
      i->repeat = -1;
    return 0;
  }
  if (event != 0 || (i->held & (1u << key)))
    return 0;
  i->held |= 1u << key;
  if (key < 2) {
    i->repeat = key;
    i->pressed = now;
    i->next = now + 400;
  }
  const int commands[] = {4, 5, 2, 3, 1, 0};
  return commands[key];
}
int np_input_tick(NpInput *i, uint32_t now, bool ready) {
  if (!ready) {
    memset(i, 0, sizeof(*i));
    i->repeat = -1;
    return 0;
  }
  if (i->repeat < 0 || i->repeat > 1 || !(i->held & (1u << i->repeat)))
    return 0;
  if ((uint32_t)(now - i->pressed) >= 10000) {
    i->repeat = -1;
    return 0;
  }
  if ((int32_t)(now - i->next) < 0)
    return 0;
  i->next = now + 125;
  return i->repeat == 0 ? 4 : 5;
}

int np_artwork_apply(NpModel *m, const NpFrame *f) {
  if (f->type != 19 || !np_validate(19, f->payload, f->length))
    return -1;
  if (!m->synced || !m->fresh || !m->epoch || np_u32(f->payload) != m->epoch ||
      np_u32(f->payload + 4) != m->revision)
    return 0;
  m->has_artwork = f->payload[10] == 1;
  if (m->has_artwork)
    memcpy(m->artwork, f->payload + 12, sizeof(m->artwork));
  else
    memset(m->artwork, 0, sizeof(m->artwork));
  return 1;
}
int np_scroll_offset(int overflow, uint32_t elapsed) {
  if (overflow <= 0)
    return 0;
  uint32_t moving = ((uint32_t)overflow * 1000 + 11) / 12;
  elapsed %= 5000 + moving + 1500;
  if (elapsed < 5000)
    return 0;
  if (elapsed >= 5000 + moving)
    return overflow;
  return (int)((elapsed - 5000) * 12 / 1000);
}
