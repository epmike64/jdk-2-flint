# jdk-2-flint
================

Making changes to OpenJDK Java compiler source code is tricky, since the compiler 
recursively calls itself, when trying to compile itself. This, in turn, breaks the compilation process, all together.
This repo moves OpenJDK compiler source code to a new package.


