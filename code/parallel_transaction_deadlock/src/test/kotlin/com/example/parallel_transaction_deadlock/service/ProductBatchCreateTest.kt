package com.example.parallel_transaction_deadlock.service

import com.example.parallel_transaction_deadlock.IntegrationTestSetup
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import kotlin.system.measureTimeMillis

@SpringBootTest
@ContextConfiguration(classes = [IntegrationTestSetup::class])
class ProductBatchCreateTest(
    private val sut: ProductBatchCreateUseCase
) : FunSpec({

    test("제품 배치 생성") {
        // given
        val commands: List<ProductBatchCreateUseCase.Command> = (0 until 10).map {
            ProductBatchCreateUseCase.Command(
                name = "제품 $it",
                description = "제품 $it 설명"
            )
        }

        // when
        val results: List<ProductBatchCreateUseCase.Result> = sut.invoke(commands)

        // then
        results.filterIsInstance<ProductBatchCreateUseCase.Result.Success>().size shouldBe 10
    }

    test("제품 배치 생성 시간 측정") {
        // given
        val commands: List<ProductBatchCreateUseCase.Command> = (0 until 10).map {
            ProductBatchCreateUseCase.Command(
                name = "제품 $it",
                description = "제품 $it 설명"
            )
        }

        // when, then
        measureTimeMillis { sut.invoke(commands) }
            .also { time -> println("제품 배치 생성 시간: $time ms") }
    }

})
