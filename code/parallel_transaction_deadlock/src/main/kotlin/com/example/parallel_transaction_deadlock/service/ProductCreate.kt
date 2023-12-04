package com.example.parallel_transaction_deadlock.service

import com.example.parallel_transaction_deadlock.domain.Product
import com.example.parallel_transaction_deadlock.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class ProductCreate(
    private val productRepository: ProductRepository
) : ProductCreateUseCase {

    @Synchronized
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun invoke(command: ProductCreateUseCase.Command): ProductCreateUseCase.Result {
        val product: Product = Product.create(
            name = command.name,
            description = command.description,
        )

        if (isDuplicateTitle(product.name)) {
            return ProductCreateUseCase.Result.Failure(
                title = product.name,
                message = "중복된 상품 명입니다."
            )
        }

        val savedProduct: Product = productRepository.save(product)

        return ProductCreateUseCase.Result.Success(id = savedProduct.id)
    }

    private fun isDuplicateTitle(title: String): Boolean {
        return productRepository.findByName(title) != null
    }
}
