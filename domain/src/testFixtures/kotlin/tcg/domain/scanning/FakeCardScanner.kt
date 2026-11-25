package tcg.domain.scanning

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

class FakeCardScanner(private val reads: List<BaseNumberRead> = emptyList()) : CardScanner {
    private val _requestedScopes = mutableListOf<ScanScope>()
    val requestedScopes: List<ScanScope> get() = _requestedScopes

    override fun scan(scope: ScanScope): Flow<BaseNumberRead> {
        _requestedScopes += scope
        return reads.asFlow()
    }
}
