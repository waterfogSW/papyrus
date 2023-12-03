package com.example.parallel_transaction_deadlock.domain

import jakarta.persistence.*

typealias PostId = Long?

@Entity
class Product(
    id: PostId = null,
    name: String,
    description: String,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: PostId = id
        private set

    @Column
    var name: String = name
        private set

    @Column
    var description: String = description
        private set

    companion object {
        fun create(
            name: String,
            description: String
        ): Product {
            return Product(
                name = name,
                description = description,
            )
        }
    }
}
