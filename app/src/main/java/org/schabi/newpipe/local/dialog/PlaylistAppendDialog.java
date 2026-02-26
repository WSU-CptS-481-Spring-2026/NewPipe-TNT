package org.schabi.newpipe.local.dialog;

import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.DEFAULT_THUMBNAIL_ID;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jakewharton.rxbinding4.widget.RxTextView;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.local.LocalItemListAdapter;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.LocalPlaylistSearch;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public final class PlaylistAppendDialog extends PlaylistDialog {
    private static final String TAG = PlaylistAppendDialog.class.getCanonicalName();

    private EditText playlistSearchInput;
    private RecyclerView playlistRecyclerView;
    private LocalItemListAdapter playlistAdapter;
    private TextView playlistDuplicateIndicator;

    private LocalPlaylistSearch playlistSearch;

    private final CompositeDisposable playlistDisposables = new CompositeDisposable();

    /**
     * Create a new instance of {@link PlaylistAppendDialog}.
     *
     * @param streamEntities    a list of {@link StreamEntity} to be added to playlists
     * @return a new instance of {@link PlaylistAppendDialog}
     */
    public static PlaylistAppendDialog newInstance(final List<StreamEntity> streamEntities) {
        final PlaylistAppendDialog dialog = new PlaylistAppendDialog();
        dialog.setStreamEntities(streamEntities);
        return dialog;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle - Creation
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_playlists, container);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final LocalPlaylistManager playlistManager =
                new LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()));

        playlistSearch = new LocalPlaylistSearch();

        playlistSearchInput = view.findViewById(R.id.customPlaylistSearchBox);
        playlistDisposables.add(RxTextView.textChanges(playlistSearchInput)
                .debounce(200, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onPlaylistSearchChange));

        playlistAdapter = new LocalItemListAdapter(getActivity());
        playlistAdapter.setSelectedListener(selectedItem -> {
            final List<StreamEntity> entities = getStreamEntities();
            if (selectedItem instanceof PlaylistDuplicatesEntry && entities != null) {
                onPlaylistSelected(playlistManager,
                        (PlaylistDuplicatesEntry) selectedItem, entities);
            }
        });

        playlistRecyclerView = view.findViewById(R.id.playlist_list);
        playlistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        playlistRecyclerView.setAdapter(playlistAdapter);

        playlistDuplicateIndicator = view.findViewById(R.id.playlist_duplicate);

        final View newPlaylistButton = view.findViewById(R.id.newPlaylist);
        newPlaylistButton.setOnClickListener(ignored -> openCreatePlaylistDialog());

        playlistDisposables.add(playlistManager
                .getPlaylistDuplicates(getStreamEntities().get(0).getUrl())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onPlaylistsReceived));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle - Destruction
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        playlistDisposables.dispose();
        if (playlistAdapter != null) {
            playlistAdapter.unsetSelectedListener();
        }

        playlistDisposables.clear();
        playlistRecyclerView = null;
        playlistAdapter = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Helper
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Filter the playlists on search query changed.
     *
     * @param searchQuery the search query
     */
    private void onPlaylistSearchChange(@NonNull final CharSequence searchQuery) {
        final List<PlaylistDuplicatesEntry> searchedPlaylists =
                playlistSearch.search(searchQuery.toString());
        playlistAdapter.clearStreamItemList();
        playlistAdapter.addItems(searchedPlaylists);
    }

    /** Display create playlist dialog. */
    public void openCreatePlaylistDialog() {
        if (getStreamEntities() == null || !isAdded()) {
            return;
        }

        final PlaylistCreationDialog playlistCreationDialog =
                PlaylistCreationDialog.newInstance(getStreamEntities());
        // Move the dismissListener to the new dialog.
        playlistCreationDialog.setOnDismissListener(this.getOnDismissListener());
        this.setOnDismissListener(null);

        playlistCreationDialog.show(getParentFragmentManager(), TAG);
        requireDialog().dismiss();
    }

    private void onPlaylistsReceived(@NonNull final List<PlaylistDuplicatesEntry> playlists) {
        if (playlistAdapter != null
                && playlistRecyclerView != null
                && playlistDuplicateIndicator != null) {
            playlistAdapter.clearStreamItemList();

            playlistSearch.receivePlaylists(playlists);

            playlistAdapter.addItems(playlists);
            playlistRecyclerView.setVisibility(View.VISIBLE);
            playlistDuplicateIndicator.setVisibility(
                    anyPlaylistContainsDuplicates(playlists) ? View.VISIBLE : View.GONE);

            onPlaylistSearchChange(playlistSearchInput.getText());
        }
    }

    private boolean anyPlaylistContainsDuplicates(final List<PlaylistDuplicatesEntry> playlists) {
        return playlists.stream()
                .anyMatch(playlist -> playlist.getTimesStreamIsContained() > 0);
    }

    private void onPlaylistSelected(@NonNull final LocalPlaylistManager manager,
                                    @NonNull final PlaylistDuplicatesEntry playlist,
                                    @NonNull final List<StreamEntity> streams) {

        final String toastText;
        if (playlist.getTimesStreamIsContained() > 0) {
            toastText = getString(R.string.playlist_add_stream_success_duplicate,
                    playlist.getTimesStreamIsContained());
        } else {
            toastText = getString(R.string.playlist_add_stream_success);
        }

        final Toast successToast = Toast.makeText(getContext(), toastText, Toast.LENGTH_SHORT);

        playlistDisposables.add(manager.appendToPlaylist(playlist.getUid(), streams)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> {
                    successToast.show();

                    if (playlist.getThumbnailStreamId() != null
                            && playlist.getThumbnailStreamId() == DEFAULT_THUMBNAIL_ID
                    ) {
                        playlistDisposables.add(manager
                                .changePlaylistThumbnail(playlist.getUid(), streams.get(0).getUid(),
                                        false)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(ignore -> successToast.show()));
                    }
                }));

        requireDialog().dismiss();
    }
}
