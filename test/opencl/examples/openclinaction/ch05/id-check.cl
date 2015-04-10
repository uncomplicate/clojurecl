__kernel void id_check (__global float *output) {

    size_t gid0 = get_global_id(0);
    size_t gid1 = get_global_id(1);
    size_t gsize0 = get_global_size(0);
    size_t offset0 = get_global_offset(0);
    size_t offset1 = get_global_offset(1);
    size_t lid0 = get_local_id(0);
    size_t lid1 = get_local_id(1);

    int index0 = gid0 - offset0;
    int index1 = gid1 - offset1;
    int index = index1 * gsize0 + index0;

    output[index] = gid0 * 10.0f + gid1 * 1.0f + lid0 * 0.1f + lid1 * 0.01f;
}
