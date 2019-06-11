__kernel void sum_reduce_horizontal (__global ACCUMULATOR* acc, __global REAL* data) {
    const uint i = get_global_size(0) * get_global_id(1) + get_global_id(0);
    const uint iacc = get_global_size(0) * get_group_id(1) + get_global_id(0);
    __local ACCUMULATOR lacc[WGS];
    const ACCUMULATOR sum = work_group_reduction_sum_2(lacc, data[i]);
    if (get_local_id(1) == 0) {
        acc[iacc] = sum;
    }
}

__kernel void sum_reduce_vertical (__global ACCUMULATOR* acc, __global REAL* data) {
    const uint i = get_global_size(1) * get_global_id(0) + get_global_id(1);
    const uint iacc = get_global_size(0) * get_group_id(1) + get_global_id(0);
    __local ACCUMULATOR lacc[WGS];
    const ACCUMULATOR sum = work_group_reduction_sum_2(lacc, data[i]);
    if (get_local_id(1) == 0) {
        acc[iacc] = sum;
    }
}
