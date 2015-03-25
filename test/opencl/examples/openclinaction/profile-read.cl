__kernel void profile_read (__global char16 *c,
                            int num) {

    for (int i=0; i < num; i++) {
        c[i] = (char16)(5);
    }

}
