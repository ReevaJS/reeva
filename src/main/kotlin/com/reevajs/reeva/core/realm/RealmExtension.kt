package com.reevajs.reeva.core.realm

interface RealmExtension {
    /**
     * @param realm The realm object this extension is attached to, which, by the
     *              time this function is being called, will be fully initialized
     */
    fun initObjects(realm: Realm)
}
