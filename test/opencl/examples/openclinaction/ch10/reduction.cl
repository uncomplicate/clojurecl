__kernel void naive_reduction(__global float* data, __global float* output) {
    float sum = 0.0;
    if (get_global_id(0) == 0) {
        for (int i = 0; i < 1048576; i++) {
            sum += data[i];
        }
    }
    *output = sum;
}

__kernel void reduction_scalar(__global float* data,
                               __local float* partial_sums,
                               __global float* output) {


    int lid = get_local_id(0);
    int gsize = get_local_size(0);

    partial_sums[lid] = data[get_global_id(0)];
    barrier(CLK_LOCAL_MEM_FENCE);

    for (int i = gsize/2; i > 0; i >>= 1) {
        if (lid < i) {
            partial_sums[lid] += partial_sums[lid + i];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    if(lid == 0) {
        output[get_group_id(0)] = partial_sums[0];
    }
}

__kernel void reduction_vector(__global float4* data,
                               __local float4* partial_sums,
                               __global float* output) {

    int lid = get_local_id(0);
    int group_size = get_local_size(0);

    partial_sums[lid] = data[get_global_id(0)];
    barrier(CLK_LOCAL_MEM_FENCE);

    for(int i = group_size/2; i>0; i >>= 1) {
        if(lid < i) {
            partial_sums[lid] += partial_sums[lid + i];
        }
          barrier(CLK_LOCAL_MEM_FENCE);
    }

    if(lid == 0) {
        output[get_group_id(0)] = dot (partial_sums[0], (float4)(1.0f));
    }
}
