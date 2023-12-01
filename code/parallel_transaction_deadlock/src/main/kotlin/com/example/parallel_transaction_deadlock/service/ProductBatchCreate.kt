package com.example.parallel_transaction_deadlock.service

import org.springframework.stereotype.Service

@Service
class ProductBatchCreate(
    private val productCreateUseCase: ProductCreateUseCase
) : ProductBatchCreateUseCase {

    override fun invoke(commands: List<ProductBatchCreateUseCase.Command>): List<ProductBatchCreateUseCase.Result> {
        val results: List<ProductCreateUseCase.Result> = commands.map {
            productCreateUseCase.invoke(
                command = ProductCreateUseCase.Command(
                    name = it.name,
                    content = it.description
                )
            )
        }

        return results.map {
            when (it) {
                is ProductCreateUseCase.Result.Success -> mapToSuccess(it)
                is ProductCreateUseCase.Result.Failure -> mapToFailure(it)
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
