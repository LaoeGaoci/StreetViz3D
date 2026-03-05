import { Metadata } from 'next';
import Layout from '../../layout/layout';

interface AppLayoutProps {
    children: React.ReactNode;
}

export const metadata: Metadata = {
    title: 'StreetViz3D',
    description: 'Semantic-driven 3D Street Visualization Platform',
    robots: { index: false, follow: false },
    viewport: { width: 'device-width', initialScale: 1 },
    openGraph: {
        type: 'website',
        title: 'StreetViz3D',
        description: 'Interactive 3D street visualization and editing platform.',
        images: ['/logo.png']
    },
    icons: {
        icon: '/favicon.ico'
    }
};

export default function AppLayout({ children }: AppLayoutProps) {
    return <Layout>{children}</Layout>;
}