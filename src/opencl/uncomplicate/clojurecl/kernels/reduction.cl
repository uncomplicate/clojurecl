#ifndef ACCUMULATOR
    #define ACCUMULATOR double
#endif

#ifndef WGS
    #define WGS 256
#endif

// ================= Sum reduction =============================================

inline ACCUMULATOR work_group_reduction_sum (const ACCUMULATOR value) {

    uint local_id = get_local_id(0);

    __local ACCUMULATOR lacc[WGS];
    lacc[local_id] = value;

    work_group_barrier(CLK_LOCAL_MEM_FENCE);

    ACCUMULATOR pacc = value;
    uint i = get_local_size(0);
    while (i > 0) {
        bool include_odd = (i > ((i >> 1) << 1)) && (local_id == ((i >> 1) - 1));
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
