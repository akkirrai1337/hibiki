(module
    (import "host" "call" (func $host_call (param i32 i32) (result i64)))
    (memory (export "memory") 2)
    (global $heap (mut i32) (i32.const 4096))
    (func (export "beakokit_reset")
        i32.const 4096
        global.set $heap
    )
    (func (export "beakokit_alloc") (param i32) (result i32)
        global.get $heap
        global.get $heap
        local.get 0
        i32.add
        global.set $heap
    )
    (func (export "beakokit_call") (param i32 i32) (result i64)
        local.get 0
        local.get 1
        call $host_call
    )
)
