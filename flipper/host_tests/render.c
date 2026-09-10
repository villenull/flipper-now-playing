#include "gui/canvas.h"
#include "np_model.h"
#include "np_protocol.h"
#include "np_view.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
void canvas_clear(Canvas *c) { u8g2_ClearBuffer(&c->u8g2); }
void canvas_set_color(Canvas *c, Color v) { u8g2_SetDrawColor(&c->u8g2, v); }
void canvas_set_font(Canvas *c, Font f) {
  u8g2_SetFont(&c->u8g2, f == FontPrimary ? u8g2_font_helvB08_tr
                                          : u8g2_font_haxrcorp4089_tr);
}
void canvas_draw_str(Canvas *c, int x, int y, const char *s) {
  u8g2_DrawStr(&c->u8g2, x, y, s);
}
void canvas_draw_box(Canvas *c, int x, int y, size_t w, size_t h) {
  u8g2_DrawBox(&c->u8g2, x, y, w, h);
}
void canvas_draw_frame(Canvas *c, int x, int y, size_t w, size_t h) {
  u8g2_DrawFrame(&c->u8g2, x, y, w, h);
}
void canvas_draw_line(Canvas *c, int x, int y, int xx, int yy) {
  u8g2_DrawLine(&c->u8g2, x, y, xx, yy);
}
void canvas_draw_circle(Canvas *c, int x, int y, size_t r) {
  u8g2_DrawCircle(&c->u8g2, x, y, r, U8G2_DRAW_ALL);
}
size_t canvas_string_width(Canvas *c, const char *s) {
  return u8g2_GetStrWidth(&c->u8g2, s);
}
static uint8_t display(u8x8_t *u, uint8_t msg, uint8_t arg, void *p) {
  (void)u;
  (void)msg;
  (void)arg;
  (void)p;
  return 1;
}
int main(int argc, char **argv) {
  if (argc != 4)
    return 2;
  FILE *in = fopen(argv[1], "r");
  if (!in)
    return 3;
  char hex[1600];
  if (!fgets(hex, sizeof(hex), in))
    return 4;
  fclose(in);
  size_t n = strcspn(hex, "\r\n") / 2;
  uint8_t bytes[788];
  if (n > 788)
    return 5;
  for (size_t i = 0; i < n; i++) {
    unsigned v;
    if (sscanf(hex + 2 * i, "%2x", &v) != 1)
      return 6;
    bytes[i] = (uint8_t)v;
  }
  NpFrame f;
  NpModel m = {0};
  if (!np_decode(bytes, n, &f) || np_model_apply(&m, &f, 0) != 1)
    return 7;
  /* Synthetic album fixture: a sun above layered hills, never runtime art. */
  for (int y = 0; y < 45; y++)
    for (int x = 0; x < 45; x++) {
      int dx = x - 31, dy = y - 12;
      bool sun = dx * dx + dy * dy < 49;
      bool mountain = y > 24 + abs(x - 16) / 2 || y > 28 + abs(x - 38) / 2;
      bool sky = ((x + 3 * y) % 13 == 0) && !sun;
      if (mountain || sky)
        m.artwork[y * 6 + x / 8] |= 1u << (x % 8);
    }
  m.has_artwork = true;
  if (!strcmp(argv[2], "no-artwork"))
    m.has_artwork = false;
  const char *overlay = "";
  if (!strcmp(argv[2], "paused")) {
    m.state = 2;
    m.speed = 0;
  }
  if (!strcmp(argv[2], "reconnecting"))
    np_freeze(&m, 0);
  if (!strcmp(argv[2], "error"))
    overlay = "Unsupported";
  if (!strcmp(argv[2], "unknown")) {
    m.flags &= ~2;
    m.duration = 0;
  }
  Canvas c = {0};
  static const u8x8_display_info_t info = {.tile_width = 16,
                                           .tile_height = 8,
                                           .pixel_width = 128,
                                           .pixel_height = 64};
  u8g2_SetupDisplay(&c.u8g2, display, display, display, display);
  c.u8g2.u8x8.display_info = &info;
  u8g2_SetupBuffer(&c.u8g2, c.buffer, 8, u8g2_ll_hvline_vertical_top_lsb,
                   &u8g2_cb_r0);
  u8g2_SetFontMode(&c.u8g2, 1);
  u8g2_SetFontPosBaseline(&c.u8g2);
  u8g2_SetDrawColor(&c.u8g2, 1);
  uint32_t render_at = !strcmp(argv[2], "long")            ? 6500
                       : !strcmp(argv[2], "scroll-wait")   ? 4999
                       : !strcmp(argv[2], "scroll-moving") ? 6000
                                                           : 0;
  np_view_draw(&c, &m, render_at, "Waiting for phone", overlay, 0);
  FILE *out = fopen(argv[3], "wb");
  if (!out)
    return 8;
  fprintf(out, "P4\n128 64\n");
  for (int y = 0; y < 64; y++)
    for (int x = 0; x < 128; x += 8) {
      unsigned b = 0;
      for (int k = 0; k < 8; k++)
        if (c.buffer[(y / 8) * 128 + x + k] & (1u << (y % 8)))
          b |= 1u << (7 - k);
      fputc(b, out);
    }
  fclose(out);
  return 0;
}
