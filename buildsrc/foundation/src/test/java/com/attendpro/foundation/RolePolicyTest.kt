package com.attendpro.foundation

import com.attendpro.foundation.domain.SystemRole
import com.attendpro.foundation.security.Capability
import com.attendpro.foundation.security.RolePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RolePolicyTest {
    @Test fun employeeCannotAdmin() = assertFalse(RolePolicy.allows(SystemRole.EMPLOYEE, Capability.STORE_ADMIN))
    @Test fun ownerCanAdminSystem() = assertTrue(RolePolicy.allows(SystemRole.SYSTEM_OWNER, Capability.SYSTEM_ADMIN))
    @Test fun agentCannotReadAttendanceReports() = assertFalse(RolePolicy.allows(SystemRole.CENTRAL_AGENT, Capability.ATTENDANCE_REPORT))
    @Test fun agentCanRegisterStore() = assertTrue(RolePolicy.allows(SystemRole.CENTRAL_AGENT, Capability.STORE_REGISTER))
    @Test fun storeManagerCanRequestPresenceProof() = assertTrue(RolePolicy.allows(SystemRole.STORE_MANAGER, Capability.PRESENCE_REQUEST))
    @Test fun employeeCanRecordOnlyOwnAttendance() {
        assertTrue(RolePolicy.allows(SystemRole.EMPLOYEE, Capability.ATTENDANCE_RECORD_OWN))
        assertFalse(RolePolicy.allows(SystemRole.EMPLOYEE, Capability.ATTENDANCE_RECORD_ANY))
    }
}
