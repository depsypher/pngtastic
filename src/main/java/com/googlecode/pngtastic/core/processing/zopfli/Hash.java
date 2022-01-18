/* Copyright 2014 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Author: eustas.ru@gmail.com (Eugene Klyuchnikov)
*/

package com.googlecode.pngtastic.core.processing.zopfli;

final class Hash {

  private static final int WINDOW_SIZE = Deflate.WINDOW_SIZE;
  private static final int WINDOW_MASK = Deflate.WINDOW_MASK;

  /* TODO(eustas): could/should it be different than WINDOW_SIZE? */
  private static final int HASH_SIZE = 0x8000;
  private static final int HASH_MASK = 0x7FFF;
  private static final int HASH_SHIFT = 5;

  private static final char[] seq = new char[WINDOW_SIZE];

  static {
    char[] seq = Hash.seq;
    for (int i = 0; i < WINDOW_SIZE; ++i) {
      seq[i] = (char) i;
    }
  }

  final int[] head = new int[HASH_SIZE];
  final char[] prev = new char[WINDOW_SIZE];
  private final int[] hashVal = new int[WINDOW_SIZE];
  final int[] same = new int[WINDOW_SIZE];
  int val;

  private final int[] head2 = new int[HASH_SIZE];
  final char[] prev2 = new char[WINDOW_SIZE];
  final int[] hashVal2 = new int[WINDOW_SIZE];

  Hash() {}

  public void init(byte[] input, int windowStart, int from, int to) {
    int[] hashVal = this.hashVal;
    int[] head = this.head;
    int[] same = this.same;
    char[] prev = this.prev;
    int[] hashVal2 = this.hashVal2;
    int[] head2 = this.head2;
    char[] prev2 = this.prev2;

    System.arraycopy(Cookie.intMOnes, 0, head, 0, HASH_SIZE);
    System.arraycopy(Cookie.intMOnes, 0, hashVal, 0, WINDOW_SIZE);
    System.arraycopy(Cookie.intZeroes, 0, same, 0, WINDOW_SIZE);
    System.arraycopy(seq, 0, prev, 0, WINDOW_SIZE);

    System.arraycopy(Cookie.intMOnes, 0, head2, 0, HASH_SIZE);
    System.arraycopy(Cookie.intMOnes, 0, hashVal2, 0, WINDOW_SIZE);
    System.arraycopy(seq, 0, prev2, 0, WINDOW_SIZE);

    if ((windowStart + 1 >= input.length) || (from + 1 >= input.length)) {
      return;
    }

    int val =
        (((input[windowStart] & 0xFF) << HASH_SHIFT) ^ (input[windowStart + 1] & 0xFF)) & HASH_MASK;

    for (int i = windowStart; i < from; ++i) {
      int hPos = i & WINDOW_MASK;
      val = ((val << HASH_SHIFT) ^ (i + 2 < to ? input[i + 2] & 0xFF : 0)) & HASH_MASK;

      hashVal[hPos] = val;
      int tmp = head[val];
      prev[hPos] = (char) (((tmp != -1) && (hashVal[tmp] == val)) ? tmp : hPos);
      head[val] = hPos;

      tmp = same[(i - 1) & WINDOW_MASK];
      if (tmp < 1) {
        tmp = 1;
      }
      tmp += i;
      byte b = input[i];
      while (tmp < to && b == input[tmp]) {
        tmp++;
      }
      tmp -= i;
      tmp--;
      same[hPos] = tmp;

      tmp = ((tmp - 3) & 0xFF) ^ val;
      hashVal2[hPos] = tmp;
      int h = head2[tmp];
      prev2[hPos] = (char) (((h != -1) && (hashVal2[h] == tmp)) ? h : hPos);
      head2[tmp] = hPos;
    }
    this.val = val;
  }

  /*private void updateHashValue(int c) {
    val = ((val << HASH_SHIFT) ^ c) & HASH_MASK;
  }*/

  public void updateHash(byte[] input, int pos, int end) {
    // WINDOW_MASK
    int hPos = pos & WINDOW_MASK;
    int val = this.val;

    val = ((val << HASH_SHIFT) ^ (pos + 2 < end ? input[pos + 2] & 0xFF : 0)) & HASH_MASK;

    hashVal[hPos] = val;
    int tmp = head[val];
    prev[hPos] = (char) (((tmp != -1) && (hashVal[tmp] == val)) ? tmp : hPos);
    head[val] = hPos;

    tmp = same[(pos - 1) & WINDOW_MASK];
    if (tmp < 1) {
      tmp = 1;
    }
    tmp += pos;
    byte b = input[pos];
    while (tmp < end && b == input[tmp]) {
      tmp++;
    }
    tmp -= pos;
    tmp--;
    same[hPos] = tmp;

    tmp = ((tmp - 3) & 0xFF) ^ val;
    hashVal2[hPos] = tmp;
    int h = head2[tmp];
    prev2[hPos] = (char) (((h != -1) && (hashVal2[h] == tmp)) ? h : hPos);
    head2[tmp] = hPos;

    this.val = val;
  }
}
