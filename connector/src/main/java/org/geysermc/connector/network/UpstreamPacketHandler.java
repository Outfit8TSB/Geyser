/*
 * Copyright (c) 2019 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.connector.network;

import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.packet.*;
import org.geysermc.api.events.player.PlayerFormResponseEvent;
import org.geysermc.api.window.CustomFormBuilder;
import org.geysermc.api.window.CustomFormWindow;
import org.geysermc.api.window.FormWindow;
import org.geysermc.api.window.component.InputComponent;
import org.geysermc.api.window.component.LabelComponent;
import org.geysermc.api.window.response.CustomFormResponse;
import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.session.cache.WindowCache;
import org.geysermc.connector.network.translators.Registry;

public class UpstreamPacketHandler extends LoggingPacketHandler {

    public UpstreamPacketHandler(GeyserConnector connector, GeyserSession session) {
        super(connector, session);
    }

    private boolean translateAndDefault(BedrockPacket packet) {
        Registry.BEDROCK.translate(packet.getClass(), packet, session);
        return defaultHandler(packet);
    }

    @Override
    public boolean handle(LoginPacket loginPacket) {
        // TODO: Implement support for multiple protocols
        if (loginPacket.getProtocolVersion() != GeyserConnector.BEDROCK_PACKET_CODEC.getProtocolVersion()) {
            connector.getLogger().debug("unsupported");
            session.getUpstream().disconnect("Unsupported Bedrock version. Are you running an outdated version?");
            return true;
        }

        AuthenticationUtils.encryptPlayerConnection(connector, session, loginPacket);

        PlayStatusPacket playStatus = new PlayStatusPacket();
        playStatus.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
        session.getUpstream().sendPacketImmediately(playStatus);

        ResourcePacksInfoPacket resourcePacksInfo = new ResourcePacksInfoPacket();
        session.getUpstream().sendPacketImmediately(resourcePacksInfo);
        return true;
    }

    @Override
    public boolean handle(ResourcePackClientResponsePacket textPacket) {
        connector.getLogger().debug("Handled " + textPacket.getClass().getSimpleName());
        switch (textPacket.getStatus()) {
            case COMPLETED:
                session.connect(connector.getRemoteServer());
                connector.getLogger().info("Player connected with username " + session.getAuthenticationData().getName());
                break;
            case HAVE_ALL_PACKS:
                ResourcePackStackPacket stack = new ResourcePackStackPacket();
                stack.setExperimental(false);
                stack.setForcedToAccept(false);
                session.getUpstream().sendPacketImmediately(stack);
                break;
            default:
                session.getUpstream().disconnect("disconnectionScreen.resourcePack");
                break;
        }

        return true;
    }

    @Override
    public boolean handle(ModalFormResponsePacket packet) {
        connector.getLogger().debug("Handled packet: " + packet.getClass().getSimpleName());
        WindowCache windowCache = session.getWindowCache();
        if (!windowCache.getWindows().containsKey(packet.getFormId()))
            return false;

        FormWindow window = windowCache.getWindows().remove(packet.getFormId());
        window.setResponse(packet.getFormData().trim());

        if (session.isLoggedIn()) {
            PlayerFormResponseEvent event = new PlayerFormResponseEvent(session, packet.getFormId(), window);
            connector.getPluginManager().runEvent(event);
        } else {
            if (window instanceof CustomFormWindow) {
                CustomFormWindow customFormWindow = (CustomFormWindow) window;
                if (!customFormWindow.getTitle().equals("Login"))
                    return false;

                CustomFormResponse response = (CustomFormResponse) customFormWindow.getResponse();
                session.authenticate(response.getInputResponses().get(2), response.getInputResponses().get(3));

                // Clear windows so authentication data isn't accidentally cached
                windowCache.getWindows().clear();
            }
        }
        return true;
    }

    @Override
    public boolean handle(MovePlayerPacket packet) {
        connector.getLogger().debug("Handled packet: " + packet.getClass().getSimpleName());
        if (!session.isLoggedIn()) {
            CustomFormWindow window = new CustomFormBuilder("Login")
                    .addComponent(new LabelComponent("Minecraft: Java Edition account authentication."))
                    .addComponent(new LabelComponent("Enter the credentials for your Minecraft: Java Edition account below."))
                    .addComponent(new InputComponent("Email/Username", "account@geysermc.org", ""))
                    .addComponent(new InputComponent("Password", "123456", ""))
                    .build();

            session.sendForm(window, 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean handle(AnimatePacket packet) {
        return translateAndDefault(packet);
    }

    @Override
    public boolean handle(CommandRequestPacket packet) {
        return translateAndDefault(packet);
    }

    @Override
    public boolean handle(TextPacket packet) {
        return translateAndDefault(packet);
    }

}