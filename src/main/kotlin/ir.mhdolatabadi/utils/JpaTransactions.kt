package ir.mhdolatabadi.utils

import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory

/** Runs [block] in a read-only EntityManager, closing it afterwards. */
fun <T> EntityManagerFactory.readOnly(block: (EntityManager) -> T): T {
    createEntityManager().use { em -> return block(em) }
}

/** Runs [block] inside a begin/commit transaction, rolling back and rethrowing on failure. */
fun <T> EntityManagerFactory.transaction(block: (EntityManager) -> T): T {
    val em = createEntityManager()
    try {
        em.transaction.begin()
        val result = block(em)
        em.transaction.commit()
        return result
    } catch (e: Exception) {
        if (em.transaction.isActive) em.transaction.rollback()
        throw e
    } finally {
        em.close()
    }
}
