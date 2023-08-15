package pub.gdt.verify

import com.sun.mail.imap.IMAPFolder
import jakarta.mail.*
import jakarta.mail.event.MessageCountEvent
import jakarta.mail.event.MessageCountListener
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.MemberJoinRequestEvent
import net.mamoe.mirai.utils.info
import java.util.*


object TsinghuaVerify : KotlinPlugin(
    JvmPluginDescription(
        id = "pub.gdt.verify",
        name = "Tsinghua Verify",
        version = "1.0.0",
    ) {
        author("Wesley F. Young")
    }
) {
    private const val PROTOCOL_NAME = "imap"
    private const val TSINGHUA_MAIL_SUFFIX = "@mails.tsinghua.edu.cn"
    private val url = URLName(
        PROTOCOL_NAME,
        TsinghuaVerifyConfig.smtpServer,
        TsinghuaVerifyConfig.smtpSSLPort,
        "", // file
        TsinghuaVerifyConfig.smtpAccount,
        TsinghuaVerifyConfig.smtpPassword
    )
    private lateinit var session: Session
    private lateinit var store: Store

    override fun onEnable() {
        TsinghuaVerifyConfig.reload()
        TsinghuaVerifyData.reload()

        // Log into mail service
        session = Session.getInstance(Properties(), null)
        session.debug = true
        store = session.getStore(url)
        store.connect()
        val folder = store.getFolder("INBOX")
        folder.open(Folder.READ_ONLY)

        folder.addMessageCountListener(object: MessageCountListener {
            override fun messagesAdded(e: MessageCountEvent) {
                for (message in e.messages) {
                    try {
                        val mimeMessage = message as MimeMessage
                        val senderAddress = (message.sender as InternetAddress).address
                        if (!senderAddress.endsWith(TSINGHUA_MAIL_SUFFIX)) continue
                        val content = mimeMessage.content
                        if (content is String) {
                            TsinghuaVerifyData.data[senderAddress] = content.toLong()
                            logger.info { "Mail received: $content from $senderAddress" }
                        }
                    } catch (_: Exception) {} // Nothing could stop me from listening
                }
            }

            override fun messagesRemoved(e: MessageCountEvent) {} // do nothing
        })

        // Keep alive
        val daemonThread = Thread {
            try {
                val supportsIdle = try {
                    (folder as IMAPFolder).idle()
                    true
                } catch (fex: FolderClosedException) {
                    throw fex
                } catch (mex: MessagingException) {
                    false
                }

                while (true) {
                    if (supportsIdle) {
                        (folder as IMAPFolder).idle()
                        println("IDLE done")
                    } else {
                        Thread.sleep(TsinghuaVerifyConfig.heartbeatInterval) // sleep for freq milliseconds
                        // This is to force the IMAP server to send us EXISTS notifications.
                        folder.messageCount
                    }
                }
            } catch (_: Exception) {} // Nothing could stop me from listening
        }
        daemonThread.isDaemon = true
        daemonThread.start()

        logger.info { "Mail service connected" }

        // Listen application events
        GlobalEventChannel
            .filterIsInstance(MemberJoinRequestEvent::class)
            .filter { event -> event.group?.id == TsinghuaVerifyConfig.groupId }
            .subscribeAlways<MemberJoinRequestEvent> { event ->
                run {
                    if (TsinghuaVerifyData.data[event.message] == event.fromId)
                        event.accept()
                    else event.reject(blackList = false, message = "学生邮箱输入错误！")
                    // 灌注永雏塔菲喵，谢谢喵
                }
            }

        logger.info { "Plugin loaded" }
    }

    override fun onDisable() {
        store.close()
    }
}

object TsinghuaVerifyConfig: AutoSavePluginConfig("TsinghuaVerifyConfig") {
    val groupId: Long by value(258975267L /* Tsinghua 2023 Group */)
    val smtpServer: String by value() // it had better be a server that supports IDLE.
    val smtpSSLPort: Int by value(993)
    val smtpAccount: String by value()
    val smtpPassword: String by value()
    val heartbeatInterval: Long by value(60000L)
}

object TsinghuaVerifyData: AutoSavePluginData("TsinghuaVerifyData") {
    val data: MutableMap<String, Long> by value(HashMap())
}