package com.github.shieru_lab

sealed class Platform {
    abstract fun greet()

    class Windows() : Platform() {
        override fun greet() {
            println("Hello from Windows!")
        }
    }
    class Linux() : Platform() {
        override fun greet() {
            println("Hello from Linux!")
        }
    }
}