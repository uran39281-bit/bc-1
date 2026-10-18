# V1.1.1 phone media space

Based on main50cd4b8, including the existing media-bin/free-placement changes. Phone Add now uses the remaining workspace as a large scrollable browser, with an80dp preview and88dp timeline drop target. Playback and timeline toolbars return when leaving Add. The redundant Inspector/Project tabs are omitted in Add; Done and Android Back return to editing without rebuilding the preview surface. Category tabs are48dp. Clip-specific timeline actions are absent when nothing is selected.

No media-engine or saved-project-schema changes. Version1.1.1/code19. Existing native phone and DeX logic is shared. New device regression measures the drawer at two phone sizes, inserts a real image asset, returns to editing and verifies save/reopen. Build/device results pending; do not call this qualified until CI completes.
