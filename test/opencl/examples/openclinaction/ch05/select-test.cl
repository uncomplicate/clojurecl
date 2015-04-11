__kernel void select_test (__global float4 *s1,
                            __global uchar2 *s2) {
    int4 mask1 = (int4)(-1, 0, -1, 0);
    float4 input1 = (float4)(0.25f, 0.5f, 0.75f, 1.0f);
    float4 input2 = (float4)(1.25f, 1.5f, 1.75f, 2.0f);
    *s1 = select(input1, input2, mask1);

    uchar2 mask2 = (uchar2)(0xAA, 0x55);
    uchar2 input3 = (uchar2)(0x0F, 0x0F);
    uchar2 input4 = (uchar2)(0x33, 0x33);
    *s2 = bitselect(input3, input4, mask2);
}
