#include "np_view.h"
#include <string.h>
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
  (void)held; /* Physical key mappings are unchanged; no control legend. */
  const char *texts[3] = {title, artist, album};
  const Font fonts[3] = {FontPrimary, FontSecondary, FontSecondary};
  const int baselines[3] = {10, 25, 40};
  const int tops[3] = {0, 14, 29};
  const int bottoms[3] = {12, 27, 43};
  for (int i = 0; i < 3; i++) {
    canvas_set_font(c, fonts[i]);
    int right = i == 0 ? 121 : 127;
    int overflow = (int)canvas_string_width(c, texts[i]) - (right - 49 + 1);
    int offset = (overlay && *overlay)
                     ? 0
                     : np_scroll_offset(overflow, now - m->scroll_anchor);
    row(c, texts[i], fonts[i], 49, right, tops[i], bottoms[i], baselines[i],
        offset);
  }
  /* Draw after clipping text so the left masks never erase the artwork. */
  if (m->has_artwork) {
    for (int y = 0; y < 45; y++)
      for (int x = 0; x < 45; x++)
        if (m->artwork[y * 6 + x / 8] & (1u << (x % 8)))
          canvas_draw_box(c, 1 + x, 1 + y, 1, 1);
  } else {
    canvas_draw_frame(c, 1, 1, 45, 45);
    canvas_draw_line(c, 18, 14, 18, 32);
    canvas_draw_line(c, 18, 14, 32, 11);
    canvas_draw_line(c, 32, 11, 32, 28);
    canvas_draw_box(c, 12, 30, 7, 5);
    canvas_draw_box(c, 26, 26, 7, 5);
  }
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
  canvas_draw_str(c, 2, 63, left);
  canvas_draw_str(c, 126 - rw, 63, right);
  int start = 2 + lw + 2, width = 126 - rw - 2 - start;
  if (width >= 8) {
    canvas_draw_frame(c, start, 58, width, 6);
    if ((m->flags & 3) == 3) {
      int fill = (int)((uint64_t)(width - 2) * pos / m->duration);
      if (fill > 0)
        canvas_draw_box(c, start + 1, 59, fill, 4);
    }
  }
  const char *message = overlay && *overlay
                            ? overlay
                            : (!m->fresh       ? "Reconnecting"
                               : m->state == 4 ? "Buffering"
                               : m->state == 5 ? "Phone needs access"
                               : m->state == 2 ? "Paused"
                               : m->state == 1 ? "Stopped"
                                               : "");
  if (*message) {
    canvas_set_font(c, FontSecondary);
    canvas_draw_str(c, 2, 55, message);
  }
}
