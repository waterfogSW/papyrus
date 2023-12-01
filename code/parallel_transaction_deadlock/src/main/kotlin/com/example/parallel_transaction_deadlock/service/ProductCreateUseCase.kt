package com.example.parallel_transaction_deadlock.service

import com.example.parallel_transaction_deadlock.domain.PostId

interface ProductCreateUseCase {

    fun invoke(command: Command): Result

    data class Command(
        val name: String,
        val content: String,
    )

    sealed class Result {
        data class Success(val id: PostId) : Result()
        data class Failure(
            val title: String,
            val message: String
        ) : Result()
    }
}
