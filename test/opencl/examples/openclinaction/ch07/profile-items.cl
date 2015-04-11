__kernel void profile_items (__global int4 *x, int num_ints) {

    int num_vectors = num_ints/(4 * get_global_size(0));
    x += get_global_id(0) * num_vectors;
    for (int i = 0; i < num_vectors; i++){
        x[i] += 1;
        x[i] *= 2;
        x[i] /= 3;
    }
}
