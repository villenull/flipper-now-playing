#include "np_protocol.h"
#include <stdio.h>
#include <string.h>
int main(void) {
  char line[1600];
  uint8_t bytes[788], out[788];
  NpFrame f;
  while (fgets(line, sizeof(line), stdin)) {
    size_t n = strcspn(line, "\r\n");
    bool ok = n % 2 == 0 && n / 2 <= 788;
    for (size_t i = 0; ok && i < n / 2; i++) {
      unsigned v;
      if (sscanf(line + 2 * i, "%2x", &v) != 1)
        ok = false;
      else
        bytes[i] = (uint8_t)v;
    }
    if (ok && np_decode(bytes, n / 2, &f)) {
      size_t len = np_encode(&f, out);
      for (size_t i = 0; i < len; i++)
        printf("%02x", out[i]);
      puts("");
    } else
      puts("INVALID");
  }
  return 0;
}
