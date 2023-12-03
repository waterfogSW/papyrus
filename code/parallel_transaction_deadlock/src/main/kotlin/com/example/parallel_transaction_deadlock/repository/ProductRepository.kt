package com.example.parallel_transaction_deadlock.repository

import com.example.parallel_transaction_deadlock.domain.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProductRepository : JpaRepository<Product, Long> {

    @Query("select * from product p where p.name = :name for update", nativeQuery = true)
    fun findByName(name: String): Product?
}
