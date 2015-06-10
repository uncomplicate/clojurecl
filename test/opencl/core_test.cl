__kernel void dumb_kernel(__global float *data, __local int* n, int m) {
    int gid = get_global_id(0);
    data[gid] = (float)gid;
}
