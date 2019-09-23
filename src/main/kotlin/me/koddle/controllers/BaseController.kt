package me.koddle.controllers

import me.koddle.tools.DatabaseAccess
import me.koddle.tools.RequestHelper
import org.koin.core.KoinComponent
import org.koin.core.inject

open class BaseController : KoinComponent {
    protected val requestHelper: RequestHelper by inject()
    protected val da: DatabaseAccess by inject()
}