__kernel void polar_rect (__global float4 *rvals,
                          __global float4 *angles,
                          __global float4 *xcoords,
                          __global float4 *ycoords) {
    *ycoords = sincos(*angles, xcoords);
    *xcoords *= *rvals;
    *ycoords *= *rvals;
}
