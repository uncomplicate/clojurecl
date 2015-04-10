__kernel void mod_round (__global float *mod_input,
                         __global float *mod_output,
                         __global float4 *round_input,
                         __global float4 *round_output) {
    mod_output[0] = fmod(mod_input[0], mod_input[1]);
    mod_output[1] = remainder(mod_input[0], mod_input[1]);

    round_output[0] = rint(*round_input);
    round_output[1] = round(*round_input);
    round_output[2] = ceil(*round_input);
    round_output[3] = floor(*round_input);
    round_output[4] = trunc(*round_input);
}
