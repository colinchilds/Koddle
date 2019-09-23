package dev.cchilds.models

class User {
    companion object {
        val NAME = "name"
        val DESCRIPTION = "description"
        val ROLES = "roles"
    }

    class Role {
        companion object {
            val ADMIN = "ADMIN"
        }
    }
}