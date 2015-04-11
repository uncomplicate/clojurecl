__kernel void user_event(__global float4 *v) {

    *v *= -1.0f;
}
