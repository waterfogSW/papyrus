package com.example.parallel_transaction_deadlock.service

import kotlinx.coroutines.*
import org.springframework.stereotype.Service

@Service
class ProductBatchCreate(
    private val productCreateUseCase: ProductCreateUseCase
) : ProductBatchCreateUseCase {

    override suspend fun invoke(commands: List<ProductBatchCreateUseCase.Command>): List<ProductBatchCreateUseCase.Result> =
        coroutineScope {
            val deferredResults: List<Deferred<ProductCreateUseCase.Result>> = commands.map { command ->
                async(Dispatchers.IO) {
                    productCreateUseCase.invoke(
                        ProductCreateUseCase.Command(
                            name = command.name,
                            description = command.description
                        )
                    )
                }
            }

            deferredResults.awaitAll().map { result ->
                when (result) {
                    is ProductCreateUseCase.Result.Success -> mapToSuccess(result)
                    is ProductCreateUseCase.Result.Failure -> mapToFailure(result)
                }
            }
        }

    private fun mapToSuccess(result: ProductCreateUseCase.Result.Success): ProductBatchCreateUseCase.Result.Success {
        return ProductBatchCreateUseCase.Result.Success(postId = result.id)
    }

    private fun mapToFailure(result: ProductCreateUseCase.Result.Failure): ProductBatchCreateUseCase.Result.Failure {
        return ProductBatchCreateUseCase.Result.Failure(
            name = result.title,
            message = result.message,
        )
    }
}
