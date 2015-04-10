__kernel void mad_test (__global uint *result) {
    uint a = 0x123456;
    uint b = 0x112233;
    uint c = 0x111111;
    result[0] = mad24(a, b, c);
    result[1] = mad_hi(a, b, c);
}
