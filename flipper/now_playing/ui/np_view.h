#pragma once
#include "../core/np_model.h"
#include <gui/canvas.h>
void np_view_draw(Canvas *c, const NpModel *model, uint32_t now,
                  const char *status, const char *overlay, uint8_t held);
