package dev.cchilds.controllers

import dev.cchilds.tools.DatabaseAccess
import dev.cchilds.tools.RequestHelper
import org.koin.core.KoinComponent
import org.koin.core.inject

open class BaseController : KoinComponent {
    protected val requestHelper: RequestHelper by inject()
    protected val da: DatabaseAccess by inject()
}