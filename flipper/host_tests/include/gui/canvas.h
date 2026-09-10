#pragma once
#include <stdint.h>
#include <stddef.h>
#include <u8g2.h>
typedef struct Canvas { u8g2_t u8g2; uint8_t buffer[1024]; } Canvas;
typedef enum { FontPrimary,FontSecondary,FontKeyboard,FontBigNumbers } Font;
typedef enum { ColorWhite,ColorBlack,ColorXOR } Color;
void canvas_clear(Canvas* c);
void canvas_set_color(Canvas* c,Color color);
void canvas_set_font(Canvas* c,Font font);
void canvas_draw_str(Canvas* c,int x,int y,const char* str);
void canvas_draw_box(Canvas* c,int x,int y,size_t w,size_t h);
void canvas_draw_frame(Canvas* c,int x,int y,size_t w,size_t h);
void canvas_draw_line(Canvas* c,int x1,int y1,int x2,int y2);
void canvas_draw_circle(Canvas* c,int x,int y,size_t r);
size_t canvas_string_width(Canvas* c,const char* str);
