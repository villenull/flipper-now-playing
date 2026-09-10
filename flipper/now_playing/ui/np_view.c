#include "np_view.h"
#include <string.h>
static int scroll(int overflow, uint32_t elapsed) {
  if (overflow <= 0)
    return 0;
  uint32_t moving = (uint32_t)overflow * 1000 / 12, cycle = 2000 + moving;
  elapsed %= cycle;
  if (elapsed < 1000)
    return 0;
  if (elapsed >= 1000 + moving)
    return overflow;
  return (int)((elapsed - 1000) * 12 / 1000);
}
static void row(Canvas *c, const char *text, Font font, int x, int right,
                int top, int bottom, int baseline, int offset) {
  canvas_set_font(c, font);
  canvas_draw_str(c, x - offset, baseline, text);
  canvas_set_color(c, ColorWhite);
  if (x > 0)
    canvas_draw_box(c, 0, top, x, bottom - top + 1);
  if (right < 127)
    canvas_draw_box(c, right + 1, top, 127 - right, bottom - top + 1);
  canvas_set_color(c, ColorBlack);
}
static void triangle(Canvas *c, int x, int y, int direction) {
  for (int n = 0; n < 5; n++)
    canvas_draw_line(c, x + direction * n, y + n, x + direction * n, y + 8 - n);
}
void np_view_draw(Canvas *c, const NpModel *m, uint32_t now, const char *status,
                  const char *overlay, uint8_t held) {
  canvas_clear(c);
  canvas_set_color(c, ColorBlack);
  canvas_set_font(c, FontSecondary);
  if (!m->synced) {
    canvas_draw_str(c, 4, 24, status && *status ? status : "Waiting for phone");
    canvas_draw_str(c, 4, 53, "Hold Back to exit");
    return;
  }
  if (!m->epoch) {
    canvas_draw_str(c, 12, 23, "Open Apple Music");
    canvas_draw_str(c, 28, 36, "Play a song");
    canvas_draw_str(c, 8, 57, "Hold Back to exit");
    if (m->fresh)
      canvas_draw_box(c, 124, 2, 4, 4);
    return;
  }
  const char *title = *m->title ? m->title : "Unknown track";
  const char *artist = *m->artist ? m->artist : "Unknown artist";
  const char *album = *m->album ? m->album : "Album unavailable";
  canvas_set_font(c, FontPrimary);
  int title_over = (int)canvas_string_width(c, title) - 99;
  canvas_set_font(c, FontSecondary);
  int album_over = (int)canvas_string_width(c, album) - 125;
  int artist_over = (int)canvas_string_width(c, artist) - 105;
  uint32_t elapsed = now - m->scroll_anchor;
  int offsets[3] = {0};
  int over[3] = {title_over, album_over, artist_over};
  uint32_t total = 0;
  for (int i = 0; i < 3; i++)
    if (over[i] > 0)
      total += 2000 + (uint32_t)over[i] * 1000 / 12;
  if (total && (!overlay || !*overlay)) {
    elapsed %= total;
    for (int i = 0; i < 3; i++)
      if (over[i] > 0) {
        uint32_t duration = 2000 + (uint32_t)over[i] * 1000 / 12;
        if (elapsed < duration) {
          offsets[i] = scroll(over[i], elapsed);
          break;
        }
        elapsed -= duration;
      }
  }
  row(c, title, FontPrimary, 22, 120, 0, 10, 8, offsets[0]);
  row(c, artist, FontSecondary, 22, 126, 11, 20, 18, offsets[2]);
  row(c, album, FontSecondary, 2, 126, 21, 29, 27, offsets[1]);
  canvas_draw_frame(c, 2, 2, 16, 16);
  canvas_draw_line(c, 9, 6, 9, 13);
  canvas_draw_line(c, 9, 6, 14, 5);
  canvas_draw_line(c, 14, 5, 14, 11);
  canvas_draw_box(c, 6, 12, 4, 3);
  canvas_draw_box(c, 11, 10, 4, 3);
  if (m->fresh)
    canvas_draw_box(c, 124, 2, 4, 4);
  else
    canvas_draw_frame(c, 124, 2, 4, 4);
  uint64_t pos = np_position(m, now);
  char left[24], right[24];
  np_time(left, sizeof(left), pos, m->flags & 1, false);
  np_time(right, sizeof(right),
          m->mode ? (m->duration > pos ? m->duration - pos : 0) : m->duration,
          m->flags & 2, m->mode);
  canvas_set_font(c, FontSecondary);
  int lw = canvas_string_width(c, left), rw = canvas_string_width(c, right);
  canvas_draw_str(c, 2, 36, left);
  canvas_draw_str(c, 126 - rw, 36, right);
  int start = 2 + lw + 2, width = 126 - rw - 2 - start;
  if (width >= 8) {
    canvas_draw_frame(c, start, 31, width, 6);
    if ((m->flags & 3) == 3) {
      int fill = (int)((uint64_t)(width - 2) * pos / m->duration);
      if (fill > 0)
        canvas_draw_box(c, start + 1, 32, fill, 4);
    }
  }
  const char *message =
      overlay && *overlay
          ? overlay
          : (!m->fresh ? "Reconnecting"
                       : (m->state == 4
                              ? "Buffering"
                              : (m->state == 5 ? "Phone needs access" : "")));
  if (*message) {
    canvas_draw_str(c, 5, 54, message);
    return;
  }
  canvas_draw_line(c, 62, 42, 66, 42);
  canvas_draw_line(c, 64, 40, 64, 44);
  canvas_draw_line(c, 62, 62, 66, 62);
  if (m->caps & 2) {
    triangle(c, 33, 49, -1);
    triangle(c, 38, 49, -1);
  } else
    canvas_draw_line(c, 29, 53, 39, 53);
  if (m->caps & 4) {
    triangle(c, 89, 49, 1);
    triangle(c, 94, 49, 1);
  } else
    canvas_draw_line(c, 89, 53, 99, 53);
  canvas_draw_circle(c, 64, 53, 6);
  if (m->caps & 1) {
    if (m->state == 3) {
      canvas_draw_box(c, 61, 50, 2, 7);
      canvas_draw_box(c, 65, 50, 2, 7);
    } else
      triangle(c, 61, 49, 1);
  }
  if (held & 1)
    canvas_draw_frame(c, 60, 39, 9, 7);
  if (held & 2)
    canvas_draw_frame(c, 60, 60, 9, 4);
  if (held & 4)
    canvas_draw_frame(c, 27, 47, 14, 12);
  if (held & 8)
    canvas_draw_frame(c, 87, 47, 14, 12);
  if (held & 16)
    canvas_draw_circle(c, 64, 53, 8);
}
