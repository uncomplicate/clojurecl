__kernel void shuffle_test (__global float8 *s1,
                            __global char16 *s2) {

    uint8 mask1 = (uint8) (1, 2, 0, 1, 3, 1, 2, 3);
    float4 input = (float4) (0.25f, 0.5f, 0.75f, 1.0f);
    *s1 = shuffle(input, mask1);

    uchar16 mask2 = (uchar16) (6, 10, 5, 2, 8, 0, 9, 14,
                               7, 5, 12, 3, 11, 15, 1, 13);
    char8 input1 = (char8)('l', 'o', 'f', 'c', 'a', 'u', 's', 'f');
    char8 input2 = (char8)('f', 'e', 'h', 't', 'n', 'n', '2', 'i');
    *s2 = shuffle2(input1, input2, mask2);

}
