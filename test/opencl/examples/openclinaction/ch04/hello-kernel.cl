__kernel void hello_kernel(__global char16 *msg) {
    *msg = (char16)('H', 'e', 'l', 'l', 'o', ' ',
                    'k', 'e', 'r', 'n', 'e', 'l', '!', '!', '!', '\0');
}
