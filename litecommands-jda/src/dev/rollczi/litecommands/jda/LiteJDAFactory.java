package dev.rollczi.litecommands.jda;

import dev.rollczi.litecommands.LiteCommandsFactory;
import dev.rollczi.litecommands.LiteCommandsBuilder;
import dev.rollczi.litecommands.argument.parser.ParserRegistry;
import dev.rollczi.litecommands.context.ContextResult;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.jda.integration.IntegrationAnnotationProcessor;
import dev.rollczi.litecommands.jda.permission.DiscordMissingPermissions;
import dev.rollczi.litecommands.jda.permission.DiscordMissingPermissionsHandler;
import dev.rollczi.litecommands.jda.permission.DiscordPermissionValidator;
import dev.rollczi.litecommands.jda.permission.DiscordPermissionAnnotationProcessor;
import dev.rollczi.litecommands.jda.visibility.VisibilityAnnotationProcessor;
import dev.rollczi.litecommands.scope.Scope;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.RestAction;

public final class LiteJDAFactory {

    private LiteJDAFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <B extends LiteCommandsBuilder<User, LiteJDASettings, B>> B builder(JDA jda) {
        JDAPlatform platform = new JDAPlatform(new LiteJDASettings(), jda);

        return (B) LiteCommandsFactory.builder(User.class, platform).self((builder, internal) -> builder
            .settings(settings -> settings.translator(createTranslator(internal.getParserRegistry())))
            .bind(JDA.class, () -> jda)
            .result(String.class, new StringHandler())
            .result(RestAction.class, new RestActionHandler())
            .result(MessageEmbed.class, new MessageEmbedHandler())

            .context(Guild.class, invocation -> from(invocation, Guild.class))
            .context(MessageChannelUnion.class, invocation -> from(invocation, MessageChannelUnion.class))
            .context(Member.class, invocation -> from(invocation, Member.class))
            .context(User.class, invocation -> from(invocation, SlashCommandInteractionEvent.class).map(member -> member.getUser()))
            .context(SlashCommandInteractionEvent.class, invocation -> from(invocation, SlashCommandInteractionEvent.class))

            .validator(Scope.global(), new DiscordPermissionValidator())
            .result(DiscordMissingPermissions.class, new DiscordMissingPermissionsHandler<>(internal.getMessageRegistry()))
            .annotations(extension -> extension
                .processor(new DiscordPermissionAnnotationProcessor<>())
                .processor(new VisibilityAnnotationProcessor<>())
                .processor(new IntegrationAnnotationProcessor<>())
            )
        );
    }

    private static JDACommandTranslator createTranslator(ParserRegistry<User> wrapperRegistry) {
        return new JDACommandTranslator(wrapperRegistry)
            .type(String.class, OptionType.STRING, option -> option.getAsString())
            .type(Long.class, OptionType.INTEGER, option -> option.getAsLong())
            .type(long.class, OptionType.INTEGER, option -> option.getAsLong())
            .type(Integer.class, OptionType.INTEGER, option -> option.getAsInt())
            .type(int.class, OptionType.INTEGER, option -> option.getAsInt())
            .type(Boolean.class, OptionType.BOOLEAN, option -> option.getAsBoolean())
            .type(boolean.class, OptionType.BOOLEAN, option -> option.getAsBoolean())
            .type(User.class, OptionType.USER, option -> option.getAsUser())
            .type(double.class, OptionType.NUMBER, option -> option.getAsDouble())
            .type(Attachment.class, OptionType.ATTACHMENT, option -> option.getAsAttachment())
            .type(Role.class, OptionType.ROLE, option -> option.getAsRole())
            .type(IMentionable.class, OptionType.MENTIONABLE, option -> option.getAsMentionable())
            .type(Channel.class, OptionType.CHANNEL, option -> option.getAsChannel())
            .type(GuildChannel.class, OptionType.CHANNEL, option -> option.getAsChannel())
            .type(GuildChannelUnion.class, OptionType.CHANNEL, option -> option.getAsChannel())
            .type(Member.class, OptionType.USER, (option) -> option.getAsMember())

            .typeOverlay(Float.class, OptionType.NUMBER, option -> option.getAsString())
            .typeOverlay(float.class, OptionType.NUMBER, option -> option.getAsString())

            .typeWrapper(Optional.class, type -> type.getParameterized(), value -> Optional.of(value))
            .typeWrapper(CompletableFuture.class, type -> type.getParameterized(), value -> CompletableFuture.completedFuture(value))
            ;
    }

    private static <T> ContextResult<T> from(Invocation<User> invocation, Class<T> type) {
        return invocation.context().get(type)
            .map(t -> ContextResult.ok(() -> t))
            .orElseGet(() -> ContextResult.error(type.getSimpleName() + " is not present"));
    }
}
