#ifdef FP_64
#pragma OPENCL EXTENSION cl_khr_fp64: enable
#endif

__kernel void double_test(__global float* a,
                          __global float* b,
                          __global float* out) {
#ifdef FP_64
    double c = (double)(*a / *b);
    *out = (float)c;
#else
    *out = *a * *b;
#endif
}
