package burp

import org.snakeyaml.engine.v1.api.Dump
import org.snakeyaml.engine.v1.api.DumpSettingsBuilder
import java.util.*
import java.util.regex.Pattern

enum class RegExpFlag {
    CASE_INSENSITIVE, MULTILINE, DOTALL, UNICODE_CASE, CANON_EQ,
    UNIX_LINES, LITERAL, UNICODE_CHARACTER_CLASS, COMMENTS;

    val value = Pattern::class.java.getField(name).getInt(null)

    override fun toString(): String {
        return name.toLowerCase().replace('_', ' ')
    }
}

enum class RequestResponse(val isRequest: Boolean) {
    REQUEST(isRequest = true) {
        override fun getMessage(rr: IHttpRequestResponse): ByteArray? = rr.request

        override fun setMessage(rr: IHttpRequestResponse, value: ByteArray) {
            rr.request = value
        }

        override fun getBodyOffset(data: ByteArray, helpers: IExtensionHelpers): Int =
                helpers.analyzeRequest(data).bodyOffset

        override fun getHeaders(data: ByteArray, helpers: IExtensionHelpers): List<String> =
                helpers.analyzeRequest(data).headers
    },

    RESPONSE(isRequest = false) {
        override fun getMessage(rr: IHttpRequestResponse): ByteArray? = rr.response

        override fun setMessage(rr: IHttpRequestResponse, value: ByteArray) {
            rr.response = value
        }

        override fun getBodyOffset(data: ByteArray, helpers: IExtensionHelpers): Int =
                helpers.analyzeResponse(data).bodyOffset

        override fun getHeaders(data: ByteArray, helpers: IExtensionHelpers): List<String> =
                helpers.analyzeResponse(data).headers
    };

    abstract fun getMessage(rr: IHttpRequestResponse): ByteArray?
    abstract fun setMessage(rr: IHttpRequestResponse, value: ByteArray)
    abstract fun getBodyOffset(data: ByteArray, helpers: IExtensionHelpers): Int
    abstract fun getHeaders(data: ByteArray, helpers: IExtensionHelpers): List<String>

    companion object {
        fun fromBoolean(isRequest: Boolean) = if (isRequest) REQUEST else RESPONSE
    }
}

enum class BurpTool {
    SUITE, TARGET, PROXY, SPIDER, SCANNER, INTRUDER, REPEATER, SEQUENCER, DECODER, COMPARER, EXTENDER;

    val value = IBurpExtenderCallbacks::class.java.getField("TOOL_$name").getInt(null)

    override fun toString(): String {
        return name.toLowerCase().capitalize()
    }
}

enum class MatchNegation(val negation: Boolean, private val description: String) {
    NORMAL(false, "Match when all the rules below apply"),
    NEGATED(true, "Match when none of the rules below apply");

    override fun toString(): String = description
}

enum class ConfigHttpListenerScope(val hls: Piper.HttpListenerScope, val inputList: List<RequestResponse>) {
    REQUEST (Piper.HttpListenerScope.REQUEST,  Collections.singletonList(RequestResponse.REQUEST)),
    RESPONSE(Piper.HttpListenerScope.RESPONSE, Collections.singletonList(RequestResponse.RESPONSE)),
    RESPONSE_WITH_REQUEST(Piper.HttpListenerScope.RESPONSE_WITH_REQUEST,
            listOf(RequestResponse.REQUEST, RequestResponse.RESPONSE)) {
        override fun toString(): String = "HTTP responses with request prepended"
    };

    override fun toString(): String = "HTTP ${hls.toString().toLowerCase()}s"

    companion object {
        fun fromHttpListenerScope(hls: Piper.HttpListenerScope): ConfigHttpListenerScope = values().first { it.hls == hls }
    }
}

enum class ConfigMinimalToolScope(val scope: Piper.MinimalTool.Scope) {
    REQUEST_RESPONSE(Piper.MinimalTool.Scope.REQUEST_RESPONSE) {
        override fun toString(): String = "HTTP requests and responses"
    },
    REQUEST_ONLY(Piper.MinimalTool.Scope.REQUEST_ONLY) {
        override fun toString(): String = "HTTP requests only"
    },
    RESPONSE_ONLY(Piper.MinimalTool.Scope.RESPONSE_ONLY) {
        override fun toString(): String = "HTTP responses only"
    };

    companion object {
        fun fromScope(scope: Piper.MinimalTool.Scope): ConfigMinimalToolScope = values().first { it.scope == scope }
    }
}

enum class ConfigFormat {
    YAML {
        override fun parse(blob: ByteArray): Piper.Config = configFromYaml(String(blob, Charsets.UTF_8))
        override fun serialize(config: Piper.Config): ByteArray =
                Dump(DumpSettingsBuilder().build()).dumpToString(config.toSettings()).toByteArray(/* default is UTF-8 */)

        override val fileExtension: String
            get() = "yaml"
    },

    PROTOBUF {
        override fun parse(blob: ByteArray): Piper.Config = Piper.Config.parseFrom(blob)
        override fun serialize(config: Piper.Config): ByteArray = config.toByteArray()
        override val fileExtension: String
            get() = "pb"
    };

    abstract fun serialize(config: Piper.Config): ByteArray
    abstract fun parse(blob: ByteArray): Piper.Config
    abstract val fileExtension: String
}

enum class MessageInfoMatchStrategy {
    ANY { override fun predicate(objects: List<MessageInfo>, check: (MessageInfo) -> Boolean): Boolean = objects.any(check) },
    ALL { override fun predicate(objects: List<MessageInfo>, check: (MessageInfo) -> Boolean): Boolean = objects.all(check) };

    abstract fun predicate(objects: List<MessageInfo>, check: (MessageInfo) -> Boolean): Boolean
}

enum class CommandInvocationPurpose {
    EXECUTE_ONLY,
    SELF_FILTER,
    MATCH_FILTER;
}