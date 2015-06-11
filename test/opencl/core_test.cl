__kernel void dumb_kernel(__global float *data, __local int* n, int m) {
    int gid = get_global_id(0);
    data[gid] = data [gid] + (float)gid + 2.0f * m;
}
