#ifndef ACCUMULATOR
#define ACCUMULATOR double
#endif

#ifndef WGS
#define WGS 256
#endif

// ================= Sum reduction =============================================

inline ACCUMULATOR work_group_reduction_sum (const ACCUMULATOR value) {

    const uint local_id = get_local_id(0);

    __local ACCUMULATOR lacc[WGS];
    lacc[local_id] = value;

    work_group_barrier(CLK_LOCAL_MEM_FENCE);

    ACCUMULATOR pacc = value;
    uint i = get_local_size(0);
    while (i > 0) {
        const bool include_odd = (i > ((i >> 1) << 1)) && (local_id == ((i >> 1) - 1));
        i >>= 1;
        if (include_odd) {
            pacc += lacc[local_id + i + 1];
        }
        if (local_id < i) {
            pacc += lacc[local_id + i];
            lacc[local_id] = pacc;
        }
        work_group_barrier(CLK_LOCAL_MEM_FENCE);
    }

    return lacc[0];
}

inline ACCUMULATOR work_group_reduction_sum_2 (const REAL value) {

    const uint local_row = get_local_id(0);
    const uint local_col = get_local_id(1);
    const uint local_m = get_local_size(0);

    __local ACCUMULATOR lacc[WGS];
    lacc[local_row + local_col * local_m] = value;

    work_group_barrier(CLK_LOCAL_MEM_FENCE);

    ACCUMULATOR pacc = value;
    uint i = get_local_size(1);
    while (i > 0) {
        const bool include_odd = (i > ((i >> 1) << 1)) && (local_col == ((i >> 1) - 1));
        i >>= 1;
        if (include_odd) {
            pacc += lacc[local_row + (local_col + i + 1) * local_m];
        }
        if (local_col < i) {
            pacc += lacc[local_row + (local_col + i) * local_m];
            lacc[local_row + local_col * local_m] = pacc;
        }
        work_group_barrier(CLK_LOCAL_MEM_FENCE);
    }

    return lacc[local_row];

}

__kernel void sum_reduction (__global ACCUMULATOR* acc) {
    const ACCUMULATOR sum = work_group_reduction_sum(acc[get_global_id(0)]);
    if (get_local_id(0) == 0) {
        acc[get_group_id(0)] = sum;
    }
}

__kernel void sum_reduction_horizontal (__global ACCUMULATOR* acc) {
    const uint i = get_global_size(0) * get_global_id(1) + get_global_id(0);
    const uint iacc = get_global_size(0) * get_group_id(1) + get_global_id(0);
    const ACCUMULATOR sum = work_group_reduction_sum_2(acc[i]);
    if (get_local_id(1) == 0) {
        acc[iacc] = sum;
    }
}

__kernel void sum_reduction_vertical (__global ACCUMULATOR* acc) {
    const uint i = get_global_size(1) * get_global_id(0) + get_global_id(1);
    const uint iacc = get_global_size(0) * get_group_id(1) + get_global_id(0);
    const ACCUMULATOR sum = work_group_reduction_sum_2(acc[i]);
    if (get_local_id(1) == 0) {
        acc[iacc] = sum;
    }
}
