__kernel void string_search (char16 pattern, __global char* text,
                             int chars_per_item, __local int* local_result,
                             __global int* global_result) {

    char16 text_vector, check_vector;

    local_result[0] = 0;
    local_result[1] = 0;
    local_result[2] = 0;
    local_result[3] = 0;

    work_group_barrier(CLK_LOCAL_MEM_FENCE);

    int item_offset = get_global_id(0) * chars_per_item;

    for (int i = item_offset; i < item_offset + chars_per_item; i++) {
        text_vector = vload16(0, text + i);

        check_vector = text_vector == pattern;

        if (all(check_vector.s0123))
            atomic_inc(local_result);
        if (all(check_vector.s4567))
            atomic_inc(local_result + 1);
        if (all(check_vector.s89AB))
            atomic_inc(local_result + 2);
        if (all(check_vector.sCDEF))
            atomic_inc(local_result + 3);

    }

    work_group_barrier(CLK_GLOBAL_MEM_FENCE);

    if(get_local_id(0) == 0) {
        atomic_add(global_result, local_result[0]);
        atomic_add(global_result + 1, local_result[1]);
        atomic_add(global_result + 2, local_result[2]);
        atomic_add(global_result + 3, local_result[3]);
    }
}
